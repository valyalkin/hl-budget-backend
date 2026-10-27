package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** FR-025, FR-026: an account or category is never deleted out from under its spendings. */
class DeleteInUseIT : AbstractIntegrationTest() {
    private lateinit var categories: Map<String, String>
    private lateinit var amex: String
    private lateinit var cash: String

    @BeforeEach
    fun setUp() {
        categories = categoryIds(alice)
        amex = createAccount(alice, "Amex")
        cash = createAccount(alice, "Cash")
    }

    @Test
    fun `an account in use cannot be deleted until its spendings move`() {
        val groceries = categories.getValue("Groceries")
        val first = createSpending(alice, groceries, amex)
        val second = createSpending(alice, groceries, amex)

        delete(alice, "/api/v1/accounts/$amex")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("in-use")
            .jsonPath("$.details.spendingCount")
            .isEqualTo(2)
        getJson(alice, "/api/v1/accounts/$amex")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.spendingCount")
            .isEqualTo(2)

        listOf(first, second).forEach {
            sendJson(alice, "PUT", "/api/v1/spendings/$it", spendingJson(groceries, cash)).expectStatus().isOk()
        }
        delete(alice, "/api/v1/accounts/$amex").expectStatus().isNoContent()
        getJson(alice, "/api/v1/accounts/$cash").expectBody().jsonPath("$.spendingCount").isEqualTo(2)
    }

    @Test
    fun `a custom category in use cannot be deleted until its spendings move`() {
        var coffee = ""
        sendJson(alice, "POST", "/api/v1/categories", """{"name":"Coffee"}""")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { coffee = it }
        val spending = createSpending(alice, coffee, amex)

        delete(alice, "/api/v1/categories/$coffee")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("in-use")
            .jsonPath("$.details.spendingCount")
            .isEqualTo(1)
        getJson(alice, "/api/v1/categories/$coffee").expectBody().jsonPath("$.spendingCount").isEqualTo(1)

        val groceries = categories.getValue("Groceries")
        sendJson(alice, "PUT", "/api/v1/spendings/$spending", spendingJson(groceries, amex)).expectStatus().isOk()
        delete(alice, "/api/v1/categories/$coffee").expectStatus().isNoContent()
    }
}
