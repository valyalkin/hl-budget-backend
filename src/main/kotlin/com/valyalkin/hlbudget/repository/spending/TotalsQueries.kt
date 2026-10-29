package com.valyalkin.hlbudget.repository.spending

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** A month with at least one spending. Months with none are not returned. */
data class MonthTotalRow(
    val month: Int,
    val total: BigDecimal,
    val spendingCount: Long,
)

/** A category with at least one spending in the range. */
data class CategoryTotalRow(
    val categoryId: UUID,
    val categoryName: String,
    val total: BigDecimal,
    val spendingCount: Long,
)

/** Sums over the caller's spendings, computed in Postgres on exact `numeric` (feature 003, R-002). */
@Repository
class TotalsQueries(
    private val jdbc: JdbcClient,
) {
    /** One row per month of [from]..[to] that has spendings; served by `ix_spendings_user_date`. */
    fun monthly(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<MonthTotalRow> =
        jdbc
            .sql(
                """
                SELECT extract(month FROM spending_date)::int AS month, sum(amount) AS total, count(*) AS n
                FROM spendings
                WHERE user_id = :userId AND spending_date BETWEEN :from AND :to
                GROUP BY 1
                """.trimIndent(),
            ).param("userId", userId)
            .param("from", from)
            .param("to", to)
            .query { rs, _ -> MonthTotalRow(rs.getInt("month"), rs.getBigDecimal("total"), rs.getLong("n")) }
            .list()

    /** One row per category with spendings in [from]..[to], largest total first, ties by name. */
    fun byCategory(
        userId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<CategoryTotalRow> =
        jdbc
            .sql(
                """
                SELECT c.id, c.name, sum(s.amount) AS total, count(*) AS n
                FROM spendings s
                JOIN categories c ON c.id = s.category_id AND c.user_id = s.user_id
                WHERE s.user_id = :userId AND s.spending_date BETWEEN :from AND :to
                GROUP BY c.id, c.name
                ORDER BY total DESC, lower(c.name), c.id
                """.trimIndent(),
            ).param("userId", userId)
            .param("from", from)
            .param("to", to)
            .query { rs, _ ->
                CategoryTotalRow(
                    rs.getObject("id", UUID::class.java),
                    rs.getString("name"),
                    rs.getBigDecimal("total"),
                    rs.getLong("n"),
                )
            }.list()
}
