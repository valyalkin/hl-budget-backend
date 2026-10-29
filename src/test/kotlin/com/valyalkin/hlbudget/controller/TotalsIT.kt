package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.TestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature 003, research R-002: GET /monthly-totals and GET /category-totals. */
class TotalsIT : AbstractIntegrationTest() {
    private lateinit var categories: Map<String, String>
    private lateinit var amex: String

    @BeforeEach
    fun data() {
        categories = categoryIds(alice)
        amex = createAccount(alice, "Amex")
    }

    private fun spend(
        category: String,
        amount: String,
        date: String,
    ) = createSpending(alice, categories.getValue(category), amex, amount = amount, date = date)

    @Test
    fun `monthly totals are exact, ordered, include empty months and give today`() {
        spend("Groceries", "84.37", "2026-10-01")
        spend("Subscriptions", "16.99", "2026-10-20")
        spend("Rent", "2150.00", "2026-09-01")
        spend("Rent", "2000.00", "2025-12-01")

        getJson(alice, "/api/v1/monthly-totals?year=2026")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.year")
            .isEqualTo(2026)
            .jsonPath("$.today")
            .isEqualTo(TestClockConfig.TODAY)
            .jsonPath("$.months.length()")
            .isEqualTo(12)
            .jsonPath("$.months[0].month")
            .isEqualTo("2026-01")
            .jsonPath("$.months[0].total.amount")
            .isEqualTo("0.00")
            .jsonPath("$.months[0].spendingCount")
            .isEqualTo(0)
            .jsonPath("$.months[8].total.amount")
            .isEqualTo("2150.00")
            .jsonPath("$.months[9].total.amount")
            .isEqualTo("101.36")
            .jsonPath("$.months[9].total.currency")
            .isEqualTo("CAD")
            .jsonPath("$.months[9].spendingCount")
            .isEqualTo(2)
            .jsonPath("$.months[11].month")
            .isEqualTo("2026-12")
    }

    @Test
    fun `the year defaults to the current one and must be between 2000 and 2100`() {
        getJson(alice, "/api/v1/monthly-totals")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.year")
            .isEqualTo(2026)
        listOf("1999", "2101").forEach { year ->
            getJson(alice, "/api/v1/monthly-totals?year=$year")
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("year")
        }
    }

    @Test
    fun `category totals list only categories with spending, largest first, adding up`() {
        spend("Groceries", "84.37", "2026-10-01")
        spend("Groceries", "92.07", "2026-10-15")
        spend("Rent", "2150.00", "2026-10-01")
        spend("Taxi", "20.00", "2026-09-30")

        var names = emptyList<String>()
        getJson(alice, "/api/v1/category-totals?from=2026-10-01&to=2026-10-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].category.name")
            .value<List<String>> { names = it }
            .jsonPath("$.items[1].total.amount")
            .isEqualTo("176.44")
            .jsonPath("$.items[1].spendingCount")
            .isEqualTo(2)
            .jsonPath("$.total.amount")
            .isEqualTo("2326.44")
            .jsonPath("$.spendingCount")
            .isEqualTo(3)
            .jsonPath("$.from")
            .isEqualTo("2026-10-01")

        assertThat(names).containsExactly("Rent", "Groceries")
    }

    @Test
    fun `a range with nothing spent is empty and zero`() {
        getJson(alice, "/api/v1/category-totals?from=2026-02-01&to=2026-02-28")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .isEmpty()
            .jsonPath("$.total.amount")
            .isEqualTo("0.00")
            .jsonPath("$.spendingCount")
            .isEqualTo(0)
    }

    @Test
    fun `a bad range is 422 naming the parameter`() {
        fun expectField(
            query: String,
            field: String,
        ) = getJson(alice, "/api/v1/category-totals?$query")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].field")
            .isEqualTo(field)

        expectField("from=2026-11-01&to=2026-10-01", "from")
        expectField("from=2025-01-01&to=2026-01-03", "to")
        expectField("to=2026-10-31", "from")
    }

    @Test
    fun `another user's spendings never count, and no token is 401`() {
        spend("Groceries", "84.37", "2026-10-01")

        getJson(bob, "/api/v1/monthly-totals?year=2026")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.months[9].spendingCount")
            .isEqualTo(0)
        getJson(bob, "/api/v1/category-totals?from=2026-10-01&to=2026-10-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .isEmpty()
        client
            .get()
            .uri("/api/v1/monthly-totals")
            .exchange()
            .expectStatus()
            .isUnauthorized()
        client
            .get()
            .uri("/api/v1/category-totals?from=2026-10-01&to=2026-10-31")
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }
}
