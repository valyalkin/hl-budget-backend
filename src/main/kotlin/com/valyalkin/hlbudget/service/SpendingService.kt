package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.CreateResult
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.Spending
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.model.SpendingFilter
import com.valyalkin.hlbudget.model.SpendingPage
import com.valyalkin.hlbudget.model.SpendingSort
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.spending.SpendingEntity
import com.valyalkin.hlbudget.repository.spending.SpendingInserts
import com.valyalkin.hlbudget.repository.spending.SpendingRepository
import com.valyalkin.hlbudget.repository.spending.SpendingSpecifications
import com.valyalkin.hlbudget.repository.spending.toModel
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The caller's spendings (US3, US4). Every lookup is scoped to the caller. */
@Service
@Transactional(readOnly = true)
@Suppress("LongParameterList")
class SpendingService(
    private val currentUser: CurrentUserService,
    private val spendings: SpendingRepository,
    private val inserts: SpendingInserts,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val rules: SpendingRules,
    private val names: NamedItemResolver,
    private val clock: Clock,
) {
    /**
     * With an [idempotencyKey], a repeat of an earlier create (same key, same body) returns the
     * spending created the first time instead of a second one (FR-036, research R-007).
     */
    @Transactional
    fun create(
        identity: VerifiedIdentity,
        command: SpendingCommand,
        idempotencyKey: String?,
    ): CreateResult {
        val userId = currentUser.resolve(identity)
        val refs = references(userId, command)
        // Over the resolved ids, so a retry that names "coffee" matches the first request (R-004).
        val fingerprint = idempotencyKey?.let { rules.fingerprint(command, refs.first.id, refs.second.id) }
        if (idempotencyKey != null && fingerprint != null) {
            spendings.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let {
                return replay(it, fingerprint, userId)
            }
        }
        val now = Instant.now(clock)
        val entity =
            SpendingEntity(
                userId = userId,
                date = command.date,
                merchant = command.merchant,
                amount = command.amount,
                categoryId = refs.first.id,
                accountId = refs.second.id,
                idempotencyKey = idempotencyKey,
                idempotencyFingerprint = fingerprint,
                createdAt = now,
                updatedAt = now,
            )
        if (inserts.insertUnlessKeyTaken(entity)) {
            val saved = spendings.findById(entity.id).orElseThrow()
            return CreateResult(saved.toModel(refs.first, refs.second, rules.currency), replayed = false)
        }
        // Another request with the same key committed first; it is now visible.
        val winner =
            idempotencyKey?.let { spendings.findByUserIdAndIdempotencyKey(userId, it) }
                ?: error("Insert skipped but no spending holds the key")
        return replay(winner, fingerprint ?: "", userId)
    }

    @Transactional
    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): Spending {
        val userId = currentUser.resolve(identity)
        return withNames(spendings.findByIdAndUserId(id, userId) ?: throw notFound(id), userId)
    }

    @Transactional
    fun update(
        identity: VerifiedIdentity,
        id: UUID,
        command: SpendingCommand,
    ): Spending {
        val userId = currentUser.resolve(identity)
        val spending = spendings.findByIdAndUserId(id, userId) ?: throw notFound(id)
        val (category, account) = references(userId, command)
        spending.date = command.date
        spending.merchant = command.merchant
        spending.amount = command.amount
        spending.categoryId = category.id
        spending.accountId = account.id
        spending.updatedAt = Instant.now(clock)
        return spendings.saveAndFlush(spending).toModel(category, account, rules.currency)
    }

    @Transactional
    fun delete(
        identity: VerifiedIdentity,
        id: UUID,
    ) {
        val userId = currentUser.resolve(identity)
        spendings.delete(spendings.findByIdAndUserId(id, userId) ?: throw notFound(id))
    }

    /** One page of the caller's spendings (US4). Names come from 2 small lookups, not one per row. */
    @Transactional
    fun list(
        identity: VerifiedIdentity,
        filter: SpendingFilter,
        page: Int,
        size: Int,
    ): SpendingPage {
        val sort = checkList(filter)
        val userId = currentUser.resolve(identity)
        val found =
            spendings.findAll(
                SpendingSpecifications.matching(userId, filter),
                PageRequest.of(page, size, SpendingSpecifications.sortOf(sort)),
            )
        val categoryNames = categories.findAllByUserId(userId).associate { it.id to it.name }
        val accountNames = accounts.findAllByUserId(userId).associate { it.id to it.name }
        val items =
            found.content.map { spending ->
                spending.toModel(
                    Ref(spending.categoryId, categoryNames.getValue(spending.categoryId)),
                    Ref(spending.accountId, accountNames.getValue(spending.accountId)),
                    rules.currency,
                )
            }
        return SpendingPage(items, page, size, found.totalElements, found.totalPages)
    }

    private fun checkList(filter: SpendingFilter): SpendingSort {
        val violations = mutableListOf<FieldViolation>()
        if (filter.from != null && filter.to != null && filter.from.isAfter(filter.to)) {
            violations += FieldViolation("#/from", "from", "Range", "must not be after to")
        }
        val sort = filter.sort?.let { SpendingSort.fromApiValue(it) }
        if (filter.sort != null && sort == null) {
            violations +=
                FieldViolation(
                    "#/sort",
                    "sort",
                    "Unknown",
                    "must be one of ${SpendingSort.API_VALUES.joinToString(", ")}",
                )
        }
        if (violations.isNotEmpty()) throw ValidationException(violations)
        return sort ?: SpendingSort.DEFAULT
    }

    /**
     * Every rule at once (FR-005): the date, amount and currency rules, and that the category and
     * account are the caller's own, given by id or by name. Another user's id gets the same answer
     * as an unknown one. A name is reused or created only when everything else is valid.
     */
    private fun references(
        userId: UUID,
        command: SpendingCommand,
    ): Pair<Ref, Ref> = names.resolve(userId, command, rules.violations(command))

    private fun replay(
        existing: SpendingEntity,
        fingerprint: String,
        userId: UUID,
    ): CreateResult {
        if (existing.idempotencyFingerprint != fingerprint) {
            throw UnprocessableEntityException(
                "The Idempotency-Key was already used for a different spending",
                mapOf("reason" to "idempotency-key-reused"),
            )
        }
        return CreateResult(withNames(existing, userId), replayed = true)
    }

    private fun withNames(
        spending: SpendingEntity,
        userId: UUID,
    ): Spending {
        val category = categories.findByIdAndUserId(spending.categoryId, userId)
        val account = accounts.findByIdAndUserId(spending.accountId, userId)
        checkNotNull(category) { "Spending ${spending.id} references a missing category" }
        checkNotNull(account) { "Spending ${spending.id} references a missing account" }
        return spending.toModel(Ref(category.id, category.name), Ref(account.id, account.name), rules.currency)
    }

    private fun notFound(id: UUID) =
        NotFoundException("Spending $id was not found", mapOf("resource" to "spending", "spendingId" to id))
}
