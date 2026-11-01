package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature 009, FR-050 to FR-053: accounts are shared, count income, and income keeps them in use. */
class AccountIncomeIT : AbstractIntegrationTest() {
    private lateinit var salary: String
    private lateinit var groceries: String
    private lateinit var debit: String
    private lateinit var brokerage: String

    @BeforeEach
    fun data() {
        salary = incomeCategoryIds(alice).getValue("Salary")
        groceries = categoryIds(alice).getValue("Groceries")
        debit = createAccount(alice, "Debit card")
        brokerage = createAccount(alice, "Brokerage")
    }

    @Test
    fun `accounts include how many income entries use them`() {
        createIncome(alice, salary, brokerage)
        createIncome(alice, salary, brokerage, date = "2026-10-01")
        createSpending(alice, groceries, brokerage)

        getJson(alice, "/api/v1/accounts/$brokerage")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.incomeCount")
            .isEqualTo(2)
            .jsonPath("$.spendingCount")
            .isEqualTo(1)
        getJson(alice, "/api/v1/accounts")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[?(@.name == 'Debit card')].incomeCount")
            .isEqualTo(listOf(0))
    }

    @Test
    fun `an account used only by income is refused with both counts`() {
        createIncome(alice, salary, brokerage)
        createIncome(alice, salary, brokerage, date = "2026-10-01")

        delete(alice, "/api/v1/accounts/$brokerage")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("in-use")
            .jsonPath("$.details.spendingCount")
            .isEqualTo(0)
            .jsonPath("$.details.incomeCount")
            .isEqualTo(2)
    }

    @Test
    fun `an account used by both reports both, and a spending-only refusal now carries incomeCount 0`() {
        createIncome(alice, salary, brokerage)
        createSpending(alice, groceries, brokerage)
        createSpending(alice, groceries, debit)

        delete(alice, "/api/v1/accounts/$brokerage")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.spendingCount")
            .isEqualTo(1)
            .jsonPath("$.details.incomeCount")
            .isEqualTo(1)
        delete(alice, "/api/v1/accounts/$debit")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.spendingCount")
            .isEqualTo(1)
            .jsonPath("$.details.incomeCount")
            .isEqualTo(0)
    }

    @Test
    fun `once its income is deleted the account can be deleted`() {
        val income = createIncome(alice, salary, brokerage)
        delete(alice, "/api/v1/incomes/$income").expectStatus().isNoContent()

        delete(alice, "/api/v1/accounts/$brokerage").expectStatus().isNoContent()
    }
}
