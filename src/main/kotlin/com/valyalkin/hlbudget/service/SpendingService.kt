package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.CreateResult
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.Spending
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.spending.SpendingEntity
import com.valyalkin.hlbudget.repository.spending.SpendingInserts
import com.valyalkin.hlbudget.repository.spending.SpendingRepository
import com.valyalkin.hlbudget.repository.spending.toModel
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
        val fingerprint = idempotencyKey?.let { rules.fingerprint(command) }
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
                categoryId = command.categoryId,
                accountId = command.accountId,
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
        spending.categoryId = command.categoryId
        spending.accountId = command.accountId
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

    /**
     * Every rule at once (FR-005): the date, amount and currency rules, and that the category and
     * account are the caller's own. Another user's id gets the same answer as an unknown one.
     */
    private fun references(
        userId: UUID,
        command: SpendingCommand,
    ): Pair<Ref, Ref> {
        val violations = rules.violations(command).toMutableList()
        val category = categories.findByIdAndUserId(command.categoryId, userId)
        val account = accounts.findByIdAndUserId(command.accountId, userId)
        if (category == null) {
            violations += FieldViolation("#/categoryId", "categoryId", "NotFound", "is not one of your categories")
        }
        if (account == null) {
            violations += FieldViolation("#/accountId", "accountId", "NotFound", "is not one of your accounts")
        }
        if (violations.isNotEmpty() || category == null || account == null) throw ValidationException(violations)
        return Ref(category.id, category.name) to Ref(account.id, account.name)
    }

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
