package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.TestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature 009, FR-064, FR-065: income totals by month, by income category and by account. */
class IncomeTotalsIT : AbstractIntegrationTest() {
    private lateinit var salary: String
    private lateinit var investments: String
    private lateinit var debit: String
    private lateinit var brokerage: String

    @BeforeEach
    fun data() {
        val ids = incomeCategoryIds(alice)
        salary = ids.getValue("Salary")
        investments = ids.getValue("Investments")
        debit = createAccount(alice, "Debit card")
        brokerage = createAccount(alice, "Brokerage")
        createIncome(alice, salary, debit, amount = "5000.00", date = "2026-10-15")
        createIncome(alice, investments, brokerage, amount = "120.25", date = "2026-10-20")
        createIncome(alice, investments, brokerage, amount = "200.25", date = "2026-10-05")
        createIncome(alice, salary, debit, amount = "5000.00", date = "2026-09-15")
        // A spending in October must not count as income, and income must not count as spending.
        createSpending(alice, categoryIds(alice).getValue("Groceries"), debit, amount = "84.37", date = "2026-10-02")
    }

    @Test
    fun `monthly income totals have twelve months, exact sums and entry counts`() {
        getJson(alice, "/api/v1/monthly-income-totals?year=2026")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.year")
            .isEqualTo(2026)
            .jsonPath("$.today")
            .isEqualTo(TestClockConfig.TODAY)
            .jsonPath("$.months.length()")
            .isEqualTo(12)
            .jsonPath("$.months[0].total.amount")
            .isEqualTo("0.00")
            .jsonPath("$.months[0].entryCount")
            .isEqualTo(0)
            .jsonPath("$.months[8].total.amount")
            .isEqualTo("5000.00")
            .jsonPath("$.months[9].total.amount")
            .isEqualTo("5320.50")
            .jsonPath("$.months[9].entryCount")
            .isEqualTo(3)
        getJson(
            alice,
            "/api/v1/monthly-income-totals",
        ).expectStatus().isOk().expectBody().jsonPath("$.year").isEqualTo(2026)
        getJson(alice, "/api/v1/monthly-income-totals?year=1999").expectStatus().isEqualTo(422)
    }

    @Test
    fun `income per category lists only used categories, largest first, with colours, adding up`() {
        var names = emptyList<String>()
        getJson(alice, "/api/v1/income-category-totals?from=2026-10-01&to=2026-10-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].category.name")
            .value<List<String>> { names = it }
            .jsonPath("$.items[0].total.amount")
            .isEqualTo("5000.00")
            .jsonPath("$.items[0].color")
            .isEqualTo("#1e5b45")
            .jsonPath("$.items[1].total.amount")
            .isEqualTo("320.50")
            .jsonPath("$.items[1].entryCount")
            .isEqualTo(2)
            .jsonPath("$.items[1].color")
            .isEqualTo("#1d4ed8")
            .jsonPath("$.total.amount")
            .isEqualTo("5320.50")
            .jsonPath("$.entryCount")
            .isEqualTo(3)

        assertThat(names).containsExactly("Salary", "Investments")
    }

    @Test
    fun `income per account uses each account's colour`() {
        var colors = emptyMap<String, String>()
        getJson(alice, "/api/v1/accounts")
            .expectBody()
            .jsonPath("$.items")
            .value<List<Map<String, Any>>> { items ->
                colors =
                    items.associate { it["name"] as String to it["color"] as String }
            }
        var names = emptyList<String>()
        getJson(alice, "/api/v1/income-account-totals?from=2026-10-01&to=2026-10-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].account.name")
            .value<List<String>> { names = it }
            .jsonPath("$.items[0].color")
            .isEqualTo(colors.getValue("Debit card"))
            .jsonPath("$.items[1].color")
            .isEqualTo(colors.getValue("Brokerage"))
            .jsonPath("$.total.amount")
            .isEqualTo("5320.50")

        assertThat(names).containsExactly("Debit card", "Brokerage")
    }

    @Test
    fun `income and spending totals never mix`() {
        getJson(alice, "/api/v1/monthly-totals?year=2026")
            .expectBody()
            .jsonPath("$.months[9].total.amount")
            .isEqualTo("84.37")
        getJson(alice, "/api/v1/category-totals?from=2026-10-01&to=2026-10-31")
            .expectBody()
            .jsonPath("$.total.amount")
            .isEqualTo("84.37")
        getJson(alice, "/api/v1/account-totals?from=2026-10-01&to=2026-10-31")
            .expectBody()
            .jsonPath("$.total.amount")
            .isEqualTo("84.37")
    }

    @Test
    fun `an empty range gives no items and a zero total, range rules match category totals`() {
        getJson(alice, "/api/v1/income-category-totals?from=2026-08-01&to=2026-08-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(0)
            .jsonPath("$.total.amount")
            .isEqualTo("0.00")
        getJson(alice, "/api/v1/income-account-totals?from=2026-11-01&to=2026-10-01")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/from")
        getJson(alice, "/api/v1/income-category-totals?from=2025-01-01&to=2026-01-03")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/to")
        client
            .get()
            .uri("/api/v1/income-account-totals?from=2026-10-01&to=2026-10-31")
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }
}
