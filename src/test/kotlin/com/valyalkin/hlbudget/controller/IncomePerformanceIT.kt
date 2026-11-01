package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/** Feature 009, SC-004: the Income tab's reads are fast for 10,000 spendings and 1,000 incomes. */
class IncomePerformanceIT : AbstractIntegrationTest() {
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
    fun `the reads of the Income tab are fast`() {
        val categories = categoryIds(alice).values.take(15)
        val incomeCategories = incomeCategoryIds(alice).values.toList()
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
        jdbcTemplate.batchUpdate(
            "INSERT INTO incomes (user_id, income_date, source, amount, category_id, account_id) " +
                "VALUES (?, ?, ?, ?::numeric, ?::uuid, ?::uuid)",
            (0 until 1_000).map { i ->
                arrayOf<Any?>(
                    userId,
                    Date.valueOf(start.plusDays((i % 1095).toLong())),
                    "Source ${i % 20}",
                    "${100 + i % 5000}.50",
                    incomeCategories[i % incomeCategories.size],
                    accounts[i % accounts.size],
                )
            },
        )

        listOf(
            "/api/v1/monthly-income-totals?year=2026",
            "/api/v1/income-category-totals?from=2026-10-01&to=2026-10-31",
            "/api/v1/income-account-totals?from=2026-10-01&to=2026-10-31",
            "/api/v1/income-sources",
            "/api/v1/incomes?from=2026-10-01&to=2026-10-31&size=100&sort=date,desc",
            "/api/v1/income-categories",
            "/api/v1/accounts",
        ).forEach { uri ->
            assertThat(median(uri)).describedAs(uri).isLessThan(Duration.ofMillis(200))
        }
    }
}
