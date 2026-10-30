package com.valyalkin.hlbudget.repository.spendingimport

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Writes an import's receipt. As with spendings (feature 002, research R-007), losing a race for
 * an Idempotency-Key with a plain INSERT would fail the statement and abort the transaction,
 * leaving no way to read the winner. `ON CONFLICT … DO NOTHING` waits for the winner to commit
 * instead, and tells the caller it lost.
 */
@Repository
class SpendingImportInserts(
    private val jdbc: JdbcClient,
) {
    /** True when the receipt was inserted; false when another import already holds its key. */
    fun insertUnlessKeyTaken(receipt: SpendingImportEntity): Boolean =
        jdbc
            .sql(
                """
                INSERT INTO spending_imports (id, user_id, idempotency_key, idempotency_fingerprint,
                  spending_count, first_date, last_date, total, created_at)
                VALUES (:id, :userId, :key, :fingerprint, :count, :firstDate, :lastDate, :total, :createdAt)
                ON CONFLICT (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                """.trimIndent(),
            ).param("id", receipt.id)
            .param("userId", receipt.userId)
            .param("key", receipt.idempotencyKey)
            .param("fingerprint", receipt.idempotencyFingerprint)
            .param("count", receipt.spendingCount)
            .param("firstDate", receipt.firstDate)
            .param("lastDate", receipt.lastDate)
            .param("total", receipt.total)
            .param("createdAt", OffsetDateTime.ofInstant(receipt.createdAt, ZoneOffset.UTC))
            .update() == 1
}
