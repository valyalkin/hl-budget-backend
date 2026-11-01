package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.CreateIncomeImportResult
import com.valyalkin.hlbudget.model.IncomeImport
import com.valyalkin.hlbudget.model.IncomeImportRowCommand
import com.valyalkin.hlbudget.model.Ledger
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.income.IncomeEntity
import com.valyalkin.hlbudget.repository.income.IncomeInserts
import com.valyalkin.hlbudget.repository.incomeimport.IncomeImportEntity
import com.valyalkin.hlbudget.repository.incomeimport.IncomeImportInserts
import com.valyalkin.hlbudget.repository.incomeimport.IncomeImportRepository
import com.valyalkin.hlbudget.repository.incomeimport.toModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Many income entries at once, all or nothing (feature 009, US5), the twin of
 * [SpendingImportService]. Category names must match the caller's income categories, account names
 * their accounts; nothing is created. Every lookup is scoped to the caller.
 */
@Service
@Suppress("LongParameterList")
class IncomeImportService(
    private val currentUser: CurrentUserService,
    private val imports: IncomeImportRepository,
    private val importInserts: IncomeImportInserts,
    private val incomeInserts: IncomeInserts,
    private val names: NamedItemResolver,
    private val rules: EntryRules,
    private val clock: Clock,
) {
    @Transactional
    fun create(
        identity: VerifiedIdentity,
        rows: List<IncomeImportRowCommand>,
        idempotencyKey: String?,
    ): CreateIncomeImportResult {
        val userId = currentUser.resolve(identity)
        checkRows(rows)

        val fingerprint = idempotencyKey?.let { rules.importFingerprint(rows) }
        if (idempotencyKey != null && fingerprint != null) {
            imports.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { return replay(it, fingerprint) }
        }

        val refs = knownNames(userId, rows)

        val now = Instant.now(clock)
        val receipt =
            IncomeImportEntity(
                userId = userId,
                idempotencyKey = idempotencyKey,
                idempotencyFingerprint = fingerprint,
                incomeCount = rows.size,
                firstDate = rows.minOf { it.date },
                lastDate = rows.maxOf { it.date },
                total = rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount }.setScale(2),
                createdAt = now,
            )
        if (!importInserts.insertUnlessKeyTaken(receipt)) {
            // Another request with the same key committed first; it is now visible.
            val winner =
                idempotencyKey?.let { imports.findByUserIdAndIdempotencyKey(userId, it) }
                    ?: error("Receipt insert skipped but no income import holds the key")
            return replay(winner, fingerprint ?: "")
        }

        incomeInserts.batchInsert(
            rows.map { row ->
                IncomeEntity(
                    userId = userId,
                    date = row.date,
                    source = row.merchant,
                    amount = row.amount,
                    categoryId = refs.category(row.categoryName).id,
                    accountId = refs.account(row.accountName).id,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        return CreateIncomeImportResult(receipt.toModel(rules.currency), replayed = false)
    }

    /** 1 to 1000 rows, each valid on its own; otherwise every problem at once (FR-005). */
    private fun checkRows(rows: List<IncomeImportRowCommand>) {
        val violations =
            if (rows.isEmpty()) {
                listOf(FieldViolation("#/$COLLECTION", COLLECTION, "Size", "must have 1 to 1000 rows"))
            } else {
                rows.flatMapIndexed { index, row -> rules.violations(row, index, COLLECTION) }
            }
        if (violations.isNotEmpty()) throw ValidationException(violations)
    }

    /** The caller's income categories and accounts by name, or a refusal naming every unknown one. */
    private fun knownNames(
        userId: UUID,
        rows: List<IncomeImportRowCommand>,
    ): NamedItemResolver.NameMaps {
        val refs = names.names(userId, Ledger.INCOMES)
        val unknown = rows.flatMapIndexed { index, row -> refs.violations(row, index) }
        if (unknown.isNotEmpty()) throw ValidationException(unknown)
        return refs
    }

    /** One of the caller's income imports; anyone else's is not found, like an unknown id. */
    @Transactional
    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): IncomeImport {
        val userId = currentUser.resolve(identity)
        val found =
            imports.findByIdAndUserId(id, userId)
                ?: throw NotFoundException(
                    "Income import $id was not found",
                    mapOf("resource" to "income-import", "importId" to id),
                )
        return found.toModel(rules.currency)
    }

    private fun replay(
        existing: IncomeImportEntity,
        fingerprint: String,
    ): CreateIncomeImportResult {
        if (existing.idempotencyFingerprint != fingerprint) {
            throw UnprocessableEntityException(
                "The Idempotency-Key was already used for a different import",
                mapOf("reason" to "idempotency-key-reused"),
            )
        }
        return CreateIncomeImportResult(existing.toModel(rules.currency), replayed = true)
    }

    private companion object {
        const val COLLECTION = "incomes"
    }
}
