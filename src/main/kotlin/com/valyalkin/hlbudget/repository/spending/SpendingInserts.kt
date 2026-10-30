package com.valyalkin.hlbudget.repository.spending

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
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
    private val batch: NamedParameterJdbcTemplate,
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

    /**
     * Inserts many spendings in one JDBC batch (feature 004). They carry no idempotency key of
     * their own: the import's receipt holds it.
     */
    fun batchInsert(spendings: List<SpendingEntity>) {
        if (spendings.isEmpty()) return
        val rows =
            spendings
                .map { spending ->
                    MapSqlParameterSource()
                        .addValue("id", spending.id)
                        .addValue("userId", spending.userId)
                        .addValue("date", spending.date)
                        .addValue("merchant", spending.merchant)
                        .addValue("amount", spending.amount)
                        .addValue("categoryId", spending.categoryId)
                        .addValue("accountId", spending.accountId)
                        .addValue("createdAt", OffsetDateTime.ofInstant(spending.createdAt, ZoneOffset.UTC))
                        .addValue("updatedAt", OffsetDateTime.ofInstant(spending.updatedAt, ZoneOffset.UTC))
                }.toTypedArray()
        val counts =
            batch.batchUpdate(
                """
                INSERT INTO spendings (id, user_id, spending_date, merchant, amount, category_id, account_id,
                  created_at, updated_at, version)
                VALUES (:id, :userId, :date, :merchant, :amount, :categoryId, :accountId, :createdAt, :updatedAt, 0)
                """.trimIndent(),
                rows,
            )
        // The driver may report SUCCESS_NO_INFO (-2) per row for a batch; anything else must be 1.
        check(counts.size == spendings.size && counts.all { it == 1 || it == SUCCESS_NO_INFO }) {
            "Expected ${spendings.size} inserted spendings"
        }
    }

    private companion object {
        const val SUCCESS_NO_INFO = -2
    }
}
