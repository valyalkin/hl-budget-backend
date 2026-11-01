package com.valyalkin.hlbudget.repository.spending

import com.valyalkin.hlbudget.model.Ledger
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

/** A merchant group with the details of its most recent spending. */
data class MerchantRow(
    val name: String,
    val spendingCount: Long,
    val lastUsedOn: LocalDate,
    val categoryId: UUID,
    val categoryName: String,
    val accountId: UUID,
    val accountName: String,
)

/**
 * The caller's merchants, derived from spendings (feature 003, research R-005). Spendings whose
 * merchant is equal ignoring case and surrounding spaces form one merchant. "Most recent" uses the
 * spending list's own order: latest date, then latest created, then id.
 *
 * For [Ledger.INCOMES] the same query groups income sources (feature 009, research R-004).
 */
@Repository
class MerchantQueries(
    private val jdbc: JdbcClient,
) {
    /** Up to [limit] merchants, most used first, ties by name. */
    fun mostUsed(
        userId: UUID,
        limit: Int,
        ledger: Ledger = Ledger.SPENDINGS,
    ): List<MerchantRow> {
        val party = ledger.partyColumn
        val table = ledger.table
        val date = ledger.dateColumn
        val categories = ledger.categoryTable
        return jdbc
            .sql(
                """
                WITH counts AS (
                  SELECT lower(btrim($party)) AS k, count(*) AS n
                  FROM $table WHERE user_id = :userId
                  GROUP BY 1
                ), latest AS (
                  SELECT DISTINCT ON (lower(btrim($party)))
                    lower(btrim($party)) AS k, $party AS merchant, $date AS spending_date, category_id, account_id
                  FROM $table WHERE user_id = :userId
                  ORDER BY lower(btrim($party)), $date DESC, created_at DESC, id DESC
                )
                SELECT l.merchant, c.n, l.spending_date,
                  cat.id AS category_id, cat.name AS category_name, acc.id AS account_id, acc.name AS account_name
                FROM latest l
                JOIN counts c ON c.k = l.k
                JOIN $categories cat ON cat.id = l.category_id AND cat.user_id = :userId
                JOIN accounts acc ON acc.id = l.account_id AND acc.user_id = :userId
                ORDER BY c.n DESC, lower(l.merchant), l.merchant
                LIMIT :limit
                """.trimIndent(),
            ).param("userId", userId)
            .param("limit", limit)
            .query { rs, _ ->
                MerchantRow(
                    name = rs.getString("merchant"),
                    spendingCount = rs.getLong("n"),
                    lastUsedOn = rs.getObject("spending_date", LocalDate::class.java),
                    categoryId = rs.getObject("category_id", UUID::class.java),
                    categoryName = rs.getString("category_name"),
                    accountId = rs.getObject("account_id", UUID::class.java),
                    accountName = rs.getString("account_name"),
                )
            }.list()
    }
}
