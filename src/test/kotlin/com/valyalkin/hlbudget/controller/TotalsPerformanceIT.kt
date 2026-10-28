package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * Feature 003, SC-005: each read the Spendings screen makes answers in well under the 1-second
 * budget for a user with 10,000 spendings.
 */
class TotalsPerformanceIT : AbstractIntegrationTest() {
    private fun median(uri: String): Duration {
        getJson(alice, uri).expectStatus().isOk()
        val runs =
            (1..5).map {
                val began = System.nanoTime()
                getJson(alice, uri).expectStatus().isOk()
                Duration.ofNanos(System.nanoTime() - began)
            }
        return runs.sorted()[2]
    }

    @Test
    fun `the four reads of the Spendings screen are fast over 10,000 spendings`() {
        val categories = categoryIds(alice).values.take(15)
        val accounts = listOf("Amex", "Visa", "Cash").map { createAccount(alice, it) }
        val userId =
            jdbcTemplate.queryForObject("SELECT id FROM users WHERE auth0_subject = ?", UUID::class.java, ALICE)
        val start = LocalDate.parse("2023-10-28")
        jdbcTemplate.batchUpdate(
            "INSERT INTO spendings (user_id, spending_date, merchant, amount, category_id, account_id) " +
                "VALUES (?, ?, ?, ?::numeric, ?::uuid, ?::uuid)",
            (0 until 10_000).map { i ->
                arrayOf<Any?>(
                    userId,
                    Date.valueOf(start.plusDays((i % 1095).toLong())),
                    "Merchant ${i % 400}",
                    "${1 + i % 500}.25",
                    categories[i % categories.size],
                    accounts[i % accounts.size],
                )
            },
        )

        listOf(
            "/api/v1/monthly-totals?year=2026",
            "/api/v1/category-totals?from=2026-10-01&to=2026-10-31",
            "/api/v1/merchants",
            "/api/v1/spendings?from=2026-10-01&to=2026-10-31&size=100&sort=date,desc",
        ).forEach { uri ->
            assertThat(median(uri)).describedAs(uri).isLessThan(Duration.ofMillis(200))
        }
    }
}
