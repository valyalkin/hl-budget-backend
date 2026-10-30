package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.CreateImportResult
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.SpendingImport
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.spending.SpendingEntity
import com.valyalkin.hlbudget.repository.spending.SpendingInserts
import com.valyalkin.hlbudget.repository.spendingimport.SpendingImportEntity
import com.valyalkin.hlbudget.repository.spendingimport.SpendingImportInserts
import com.valyalkin.hlbudget.repository.spendingimport.SpendingImportRepository
import com.valyalkin.hlbudget.repository.spendingimport.toModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Many spendings at once, all or nothing (feature 004, research R-006, data-model.md "Write:
 * create an import"). Every lookup is scoped to the caller.
 */
@Service
@Suppress("LongParameterList")
class SpendingImportService(
    private val currentUser: CurrentUserService,
    private val imports: SpendingImportRepository,
    private val importInserts: SpendingImportInserts,
    private val spendingInserts: SpendingInserts,
    private val names: NamedItemResolver,
    private val rules: SpendingRules,
    private val clock: Clock,
) {
    /**
     * Creates every row as a spending in one transaction, creating each missing category and
     * account name once. Nothing is written unless every row is valid (FR-033). With an
     * [idempotencyKey], a repeat with the same rows returns the first import (FR-034).
     */
    @Transactional
    fun create(
        identity: VerifiedIdentity,
        rows: List<ImportRowCommand>,
        idempotencyKey: String?,
    ): CreateImportResult {
        val userId = currentUser.resolve(identity)
        if (rows.isEmpty()) {
            throw ValidationException(
                listOf(FieldViolation("#/spendings", "spendings", "Size", "must have 1 to 1000 rows")),
            )
        }
        val violations = rows.flatMapIndexed { index, row -> rules.violations(row, index) }
        if (violations.isNotEmpty()) throw ValidationException(violations)

        val fingerprint = idempotencyKey?.let { rules.importFingerprint(rows) }
        if (idempotencyKey != null && fingerprint != null) {
            imports.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.let { return replay(it, fingerprint) }
        }

        val now = Instant.now(clock)
        val receipt =
            SpendingImportEntity(
                userId = userId,
                idempotencyKey = idempotencyKey,
                idempotencyFingerprint = fingerprint,
                spendingCount = rows.size,
                firstDate = rows.minOf { it.date },
                lastDate = rows.maxOf { it.date },
                total = rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.amount }.setScale(2),
                createdAt = now,
            )
        if (!importInserts.insertUnlessKeyTaken(receipt)) {
            // Another request with the same key committed first; it is now visible.
            val winner =
                idempotencyKey?.let { imports.findByUserIdAndIdempotencyKey(userId, it) }
                    ?: error("Receipt insert skipped but no import holds the key")
            return replay(winner, fingerprint ?: "")
        }

        val refs = names.resolveNames(userId, rows.map { it.categoryName }, rows.map { it.accountName }, now)
        spendingInserts.batchInsert(
            rows.map { row ->
                SpendingEntity(
                    userId = userId,
                    date = row.date,
                    merchant = row.merchant,
                    amount = row.amount,
                    categoryId = refs.category(row.categoryName).id,
                    accountId = refs.account(row.accountName).id,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        return CreateImportResult(receipt.toModel(rules.currency), replayed = false)
    }

    /** One of the caller's imports; anyone else's is not found, like an unknown id. */
    @Transactional
    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): SpendingImport {
        val userId = currentUser.resolve(identity)
        val found =
            imports.findByIdAndUserId(id, userId)
                ?: throw NotFoundException(
                    "Spending import $id was not found",
                    mapOf("resource" to "spending-import", "importId" to id),
                )
        return found.toModel(rules.currency)
    }

    private fun replay(
        existing: SpendingImportEntity,
        fingerprint: String,
    ): CreateImportResult {
        if (existing.idempotencyFingerprint != fingerprint) {
            throw UnprocessableEntityException(
                "The Idempotency-Key was already used for a different import",
                mapOf("reason" to "idempotency-key-reused"),
            )
        }
        return CreateImportResult(existing.toModel(rules.currency), replayed = true)
    }
}
