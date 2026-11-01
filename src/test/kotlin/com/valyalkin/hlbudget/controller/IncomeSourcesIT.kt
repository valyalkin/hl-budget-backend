package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature 009, FR-063: past income sources for autofill, from income entries only. */
class IncomeSourcesIT : AbstractIntegrationTest() {
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
    }

    @Test
    fun `sources equal ignoring case and spaces are one, named after the most recent entry`() {
        createIncome(alice, salary, debit, date = "2026-08-15", source = "Acme Corp")
        createIncome(alice, salary, debit, date = "2026-09-15", source = "Acme Corp")
        createIncome(alice, salary, debit, date = "2026-07-15", source = "Acme Corp")
        createIncome(alice, investments, brokerage, date = "2026-10-15", source = "acme corp")
        createIncome(alice, investments, brokerage, date = "2026-10-01", source = "Wealthsimple")

        getJson(alice, "/api/v1/income-sources")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(2)
            .jsonPath("$.items[0].name")
            .isEqualTo("acme corp")
            .jsonPath("$.items[0].incomeCount")
            .isEqualTo(4)
            .jsonPath("$.items[0].lastUsedOn")
            .isEqualTo("2026-10-15")
            .jsonPath("$.items[0].lastCategory.name")
            .isEqualTo("Investments")
            .jsonPath("$.items[0].lastAccount.name")
            .isEqualTo("Brokerage")
            .jsonPath("$.truncated")
            .isEqualTo(false)
    }

    @Test
    fun `merchants and sources never mix`() {
        createIncome(alice, salary, debit, source = "Acme Corp")
        createSpending(alice, categoryIds(alice).getValue("Groceries"), debit, merchant = "Loblaws")

        var sources = emptyList<String>()
        getJson(alice, "/api/v1/income-sources").expectBody().jsonPath("$.items[*].name").value<List<String>> {
            sources =
                it
        }
        var merchants = emptyList<String>()
        getJson(alice, "/api/v1/merchants").expectBody().jsonPath("$.items[*].name").value<List<String>> {
            merchants =
                it
        }

        assertThat(sources).containsExactly("Acme Corp")
        assertThat(merchants).containsExactly("Loblaws")
    }

    @Test
    fun `limit is 1 to 1000 and truncation is reported`() {
        createIncome(alice, salary, debit, source = "A")
        createIncome(alice, salary, debit, source = "B")

        getJson(alice, "/api/v1/income-sources?limit=1")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(1)
            .jsonPath("$.truncated")
            .isEqualTo(true)
        getJson(alice, "/api/v1/income-sources?limit=0").expectStatus().isEqualTo(422)
        getJson(alice, "/api/v1/income-sources?limit=1001").expectStatus().isEqualTo(422)
    }
}
