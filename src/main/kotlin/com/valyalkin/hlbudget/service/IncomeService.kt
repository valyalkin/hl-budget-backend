package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.CreateIncomeResult
import com.valyalkin.hlbudget.model.Income
import com.valyalkin.hlbudget.model.IncomeCommand
import com.valyalkin.hlbudget.model.IncomeFilter
import com.valyalkin.hlbudget.model.IncomePage
import com.valyalkin.hlbudget.model.Ledger
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.SpendingSort
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.income.IncomeEntity
import com.valyalkin.hlbudget.repository.income.IncomeInserts
import com.valyalkin.hlbudget.repository.income.IncomeRepository
import com.valyalkin.hlbudget.repository.income.IncomeSpecifications
import com.valyalkin.hlbudget.repository.income.toModel
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The caller's income entries (feature 009, US1, US2), the twin of [SpendingService]: the same
 * rules, idempotent create, paging, filters and sorts. The category must be one of the caller's
 * income categories (FR-013). Every lookup is scoped to the caller.
 */
@Service
@Transactional(readOnly = true)
@Suppress("LongParameterList")
class IncomeService(
    private val currentUser: CurrentUserService,
    private val incomes: IncomeRepository,
    private val inserts: IncomeInserts,
    private val categories: IncomeCategoryRepository,
    private val accounts: AccountRepository,
    private val rules: EntryRules,
    private val names: NamedItemResolver,
    private val clock: Clock,
) {
    /** With an [idempotencyKey], a repeat (same key, same body) returns the income created the first time. */
    @Transactional
    fun create(
        identity: VerifiedIdentity,
        command: IncomeCommand,
        idempotencyKey: String?,
    ): CreateIncomeResult {
        val userId = currentUser.resolve(identity)
        val refs = references(userId, command)
        val fingerprint = idempotencyKey?.let { rules.fingerprint(command, refs.first.id, refs.second.id) }
        if (idempotencyKey != null && fingerprint != null) {
            incomes.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let {
                return replay(it, fingerprint, userId)
            }
        }
        val now = Instant.now(clock)
        val entity =
            IncomeEntity(
                userId = userId,
                date = command.date,
                source = command.merchant,
                amount = command.amount,
                categoryId = refs.first.id,
                accountId = refs.second.id,
                idempotencyKey = idempotencyKey,
                idempotencyFingerprint = fingerprint,
                createdAt = now,
                updatedAt = now,
            )
        if (inserts.insertUnlessKeyTaken(entity)) {
            val saved = incomes.findById(entity.id).orElseThrow()
            return CreateIncomeResult(saved.toModel(refs.first, refs.second, rules.currency), replayed = false)
        }
        // Another request with the same key committed first; it is now visible.
        val winner =
            idempotencyKey?.let { incomes.findByUserIdAndIdempotencyKey(userId, it) }
                ?: error("Insert skipped but no income holds the key")
        return replay(winner, fingerprint ?: "", userId)
    }

    @Transactional
    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): Income {
        val userId = currentUser.resolve(identity)
        return withNames(incomes.findByIdAndUserId(id, userId) ?: throw notFound(id), userId)
    }

    @Transactional
    fun update(
        identity: VerifiedIdentity,
        id: UUID,
        command: IncomeCommand,
    ): Income {
        val userId = currentUser.resolve(identity)
        val income = incomes.findByIdAndUserId(id, userId) ?: throw notFound(id)
        val (category, account) = references(userId, command)
        income.date = command.date
        income.source = command.merchant
        income.amount = command.amount
        income.categoryId = category.id
        income.accountId = account.id
        income.updatedAt = Instant.now(clock)
        return incomes.saveAndFlush(income).toModel(category, account, rules.currency)
    }

    @Transactional
    fun delete(
        identity: VerifiedIdentity,
        id: UUID,
    ) {
        val userId = currentUser.resolve(identity)
        incomes.delete(incomes.findByIdAndUserId(id, userId) ?: throw notFound(id))
    }

    /** One page of the caller's income. Names come from 2 small lookups, not one per row. */
    @Transactional
    fun list(
        identity: VerifiedIdentity,
        filter: IncomeFilter,
        page: Int,
        size: Int,
    ): IncomePage {
        val sort = checkList(filter)
        val userId = currentUser.resolve(identity)
        val found =
            incomes.findAll(
                IncomeSpecifications.matching(userId, filter),
                PageRequest.of(page, size, IncomeSpecifications.sortOf(sort)),
            )
        val categoryNames = categories.findAllByUserId(userId).associate { it.id to it.name }
        val accountNames = accounts.findAllByUserId(userId).associate { it.id to it.name }
        val items =
            found.content.map { income ->
                income.toModel(
                    Ref(income.categoryId, categoryNames.getValue(income.categoryId)),
                    Ref(income.accountId, accountNames.getValue(income.accountId)),
                    rules.currency,
                )
            }
        return IncomePage(items, page, size, found.totalElements, found.totalPages)
    }

    private fun checkList(filter: IncomeFilter): SpendingSort {
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

    /** Every rule at once: date, amount and currency, and the caller's own income category and account. */
    private fun references(
        userId: UUID,
        command: IncomeCommand,
    ): Pair<Ref, Ref> = names.resolve(userId, command, rules.violations(command), Ledger.INCOMES)

    private fun replay(
        existing: IncomeEntity,
        fingerprint: String,
        userId: UUID,
    ): CreateIncomeResult {
        if (existing.idempotencyFingerprint != fingerprint) {
            throw UnprocessableEntityException(
                "The Idempotency-Key was already used for a different income",
                mapOf("reason" to "idempotency-key-reused"),
            )
        }
        return CreateIncomeResult(withNames(existing, userId), replayed = true)
    }

    private fun withNames(
        income: IncomeEntity,
        userId: UUID,
    ): Income {
        val category = categories.findByIdAndUserId(income.categoryId, userId)
        val account = accounts.findByIdAndUserId(income.accountId, userId)
        checkNotNull(category) { "Income ${income.id} references a missing income category" }
        checkNotNull(account) { "Income ${income.id} references a missing account" }
        return income.toModel(Ref(category.id, category.name), Ref(account.id, account.name), rules.currency)
    }

    private fun notFound(id: UUID) =
        NotFoundException("Income $id was not found", mapOf("resource" to "income", "incomeId" to id))
}
