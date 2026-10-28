package com.valyalkin.hlbudget.repository.spending

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The one spending write that cannot go through JPA. Losing a race for an idempotency key with a
 * plain INSERT would fail the statement and abort the transaction, leaving no way to read the
 * winner. `ON CONFLICT … DO NOTHING` waits for the winner to commit instead, and tells the caller
 * it lost (research R-007).
 */
@Repository
class SpendingInserts(
    private val jdbc: JdbcClient,
) {
    /** True when the row was inserted; false when another spending already holds its key. */
    fun insertUnlessKeyTaken(spending: SpendingEntity): Boolean =
        jdbc
            .sql(
                """
                INSERT INTO spendings (id, user_id, spending_date, merchant, amount, category_id, account_id,
                  idempotency_key, idempotency_fingerprint, created_at, updated_at, version)
                VALUES (:id, :userId, :date, :merchant, :amount, :categoryId, :accountId,
                  :key, :fingerprint, :createdAt, :updatedAt, 0)
                ON CONFLICT (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                """.trimIndent(),
            ).param("id", spending.id)
            .param("userId", spending.userId)
            .param("date", spending.date)
            .param("merchant", spending.merchant)
            .param("amount", spending.amount)
            .param("categoryId", spending.categoryId)
            .param("accountId", spending.accountId)
            .param("key", spending.idempotencyKey)
            .param("fingerprint", spending.idempotencyFingerprint)
            .param("createdAt", OffsetDateTime.ofInstant(spending.createdAt, ZoneOffset.UTC))
            .param("updatedAt", OffsetDateTime.ofInstant(spending.updatedAt, ZoneOffset.UTC))
            .update() == 1
}
