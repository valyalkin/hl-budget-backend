package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/** SC-004: a filtered page for a user with 10,000 spendings returns in under a second. */
class SpendingListPerformanceIT : AbstractIntegrationTest() {
    @Test
    fun `a filtered page over 10,000 spendings is fast`() {
        val categories = categoryIds(alice)
        val category = categories.getValue("Groceries")
        val others = listOf(category, categories.getValue("Taxi"), categories.getValue("Rent"))
        val account = createAccount(alice, "Amex")
        val userId =
            jdbcTemplate.queryForObject("SELECT id FROM users WHERE auth0_subject = ?", UUID::class.java, ALICE)
        val start = LocalDate.parse("2025-11-01")
        jdbcTemplate.batchUpdate(
            "INSERT INTO spendings (user_id, spending_date, merchant, amount, category_id, account_id) " +
                "VALUES (?, ?, 'Merchant', ?::numeric, ?::uuid, ?::uuid)",
            (0 until 10_000).map { i ->
                arrayOf<Any?>(
                    userId,
                    Date.valueOf(start.plusDays((i % 360).toLong())),
                    "${1 + i % 500}.25",
                    others[
                        i %
                            3,
                    ],
                    account,
                )
            },
        )
        val uri =
            "/api/v1/spendings?from=2026-01-01&to=2026-06-30&categoryId=$category&sort=amount,desc&size=100"
        getJson(alice, uri).expectStatus().isOk()

        val began = System.nanoTime()
        getJson(alice, uri)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(100)
        val took = Duration.ofNanos(System.nanoTime() - began)

        assertThat(took).isLessThan(Duration.ofSeconds(1))
    }
}
