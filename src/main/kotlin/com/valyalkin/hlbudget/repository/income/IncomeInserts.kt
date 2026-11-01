package com.valyalkin.hlbudget.repository.income

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The one income write that cannot go through JPA (the twin of SpendingInserts). Losing a race for
 * an idempotency key with a plain INSERT would fail the statement and abort the transaction, leaving
 * no way to read the winner. `ON CONFLICT … DO NOTHING` waits for the winner to commit instead.
 */
@Repository
class IncomeInserts(
    private val jdbc: JdbcClient,
    private val batch: NamedParameterJdbcTemplate,
) {
    /** True when the row was inserted; false when another income already holds its key. */
    fun insertUnlessKeyTaken(income: IncomeEntity): Boolean =
        jdbc
            .sql(
                """
                INSERT INTO incomes (id, user_id, income_date, source, amount, category_id, account_id,
                  idempotency_key, idempotency_fingerprint, created_at, updated_at, version)
                VALUES (:id, :userId, :date, :source, :amount, :categoryId, :accountId,
                  :key, :fingerprint, :createdAt, :updatedAt, 0)
                ON CONFLICT (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                """.trimIndent(),
            ).param("id", income.id)
            .param("userId", income.userId)
            .param("date", income.date)
            .param("source", income.source)
            .param("amount", income.amount)
            .param("categoryId", income.categoryId)
            .param("accountId", income.accountId)
            .param("key", income.idempotencyKey)
            .param("fingerprint", income.idempotencyFingerprint)
            .param("createdAt", OffsetDateTime.ofInstant(income.createdAt, ZoneOffset.UTC))
            .param("updatedAt", OffsetDateTime.ofInstant(income.updatedAt, ZoneOffset.UTC))
            .update() == 1

    /**
     * Inserts many incomes in one JDBC batch (an income import). They carry no idempotency key of
     * their own: the import's receipt holds it.
     */
    fun batchInsert(incomes: List<IncomeEntity>) {
        if (incomes.isEmpty()) return
        val rows =
            incomes
                .map { income ->
                    MapSqlParameterSource()
                        .addValue("id", income.id)
                        .addValue("userId", income.userId)
                        .addValue("date", income.date)
                        .addValue("source", income.source)
                        .addValue("amount", income.amount)
                        .addValue("categoryId", income.categoryId)
                        .addValue("accountId", income.accountId)
                        .addValue("createdAt", OffsetDateTime.ofInstant(income.createdAt, ZoneOffset.UTC))
                        .addValue("updatedAt", OffsetDateTime.ofInstant(income.updatedAt, ZoneOffset.UTC))
                }.toTypedArray()
        val counts =
            batch.batchUpdate(
                """
                INSERT INTO incomes (id, user_id, income_date, source, amount, category_id, account_id,
                  created_at, updated_at, version)
                VALUES (:id, :userId, :date, :source, :amount, :categoryId, :accountId, :createdAt, :updatedAt, 0)
                """.trimIndent(),
                rows,
            )
        // The driver may report SUCCESS_NO_INFO (-2) per row for a batch; anything else must be 1.
        check(counts.size == incomes.size && counts.all { it == 1 || it == SUCCESS_NO_INFO }) {
            "Expected ${incomes.size} inserted incomes"
        }
    }

    private companion object {
        const val SUCCESS_NO_INFO = -2
    }
}
