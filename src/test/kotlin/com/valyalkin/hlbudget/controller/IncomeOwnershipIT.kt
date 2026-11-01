package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature 009, SC-006: no user can read, change, delete or reference another user's income. */
class IncomeOwnershipIT : AbstractIntegrationTest() {
    private lateinit var aliceSalary: String
    private lateinit var aliceAccount: String
    private lateinit var aliceIncome: String
    private lateinit var aliceCustom: String
    private lateinit var aliceImport: String
    private lateinit var bobSalary: String
    private lateinit var bobAccount: String

    @BeforeEach
    fun data() {
        aliceSalary = incomeCategoryIds(alice).getValue("Salary")
        aliceAccount = createAccount(alice, "Debit card")
        aliceIncome = createIncome(alice, aliceSalary, aliceAccount, source = "Alice Employer")
        sendJson(alice, "POST", "/api/v1/income-categories", """{"name":"Gifts"}""")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { aliceCustom = it }
        sendJson(
            alice,
            "POST",
            "/api/v1/income-imports",
            """{"incomes":[{"date":"2026-10-01","source":"Broker","amount":{"amount":"10.00","currency":"CAD"},""" +
                """"categoryName":"Investments","accountName":"Debit card"}]}""",
        ).expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { aliceImport = it }
        bobSalary = incomeCategoryIds(bob).getValue("Salary")
        bobAccount = createAccount(bob, "Bob card")
    }

    @Test
    fun `another user's income is not found for get, replace and delete`() {
        getJson(bob, "/api/v1/incomes/$aliceIncome").expectStatus().isNotFound()
        sendJson(
            bob,
            "PUT",
            "/api/v1/incomes/$aliceIncome",
            incomeJson(bobSalary, bobAccount),
        ).expectStatus().isNotFound()
        delete(bob, "/api/v1/incomes/$aliceIncome").expectStatus().isNotFound()
        getJson(alice, "/api/v1/incomes/$aliceIncome").expectStatus().isOk()
    }

    @Test
    fun `another user's income category is not found for get, rename and delete`() {
        getJson(bob, "/api/v1/income-categories/$aliceCustom").expectStatus().isNotFound()
        sendJson(
            bob,
            "PUT",
            "/api/v1/income-categories/$aliceCustom",
            """{"name":"Mine"}""",
        ).expectStatus().isNotFound()
        delete(bob, "/api/v1/income-categories/$aliceCustom").expectStatus().isNotFound()
    }

    @Test
    fun `another user's income import is not found`() {
        getJson(bob, "/api/v1/income-imports/$aliceImport").expectStatus().isNotFound()
        getJson(alice, "/api/v1/income-imports/$aliceImport").expectStatus().isOk()
    }

    @Test
    fun `another user's income category or account cannot be referenced`() {
        sendJson(bob, "POST", "/api/v1/incomes", incomeJson(aliceSalary, bobAccount))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/categoryId")
        sendJson(bob, "POST", "/api/v1/incomes", incomeJson(bobSalary, aliceAccount))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/accountId")
        sendJson(
            bob,
            "POST",
            "/api/v1/income-imports",
            """{"incomes":[{"date":"2026-10-01","source":"X","amount":{"amount":"1.00","currency":"CAD"},""" +
                """"categoryName":"Gifts","accountName":"Debit card"}]}""",
        ).expectStatus()
            .isEqualTo(422)
    }

    @Test
    fun `another user's income never shows in lists, sources or totals`() {
        getJson(bob, "/api/v1/incomes")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalItems")
            .isEqualTo(0)
        getJson(
            bob,
            "/api/v1/income-sources",
        ).expectStatus().isOk().expectBody().jsonPath("$.items.length()").isEqualTo(0)
        getJson(bob, "/api/v1/monthly-income-totals?year=2026")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.months[9].entryCount")
            .isEqualTo(0)
        getJson(bob, "/api/v1/income-category-totals?from=2026-10-01&to=2026-10-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(0)
        getJson(bob, "/api/v1/income-account-totals?from=2026-10-01&to=2026-10-31")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(0)
    }
}
