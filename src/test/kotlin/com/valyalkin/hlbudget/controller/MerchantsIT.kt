package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Feature 003, FR-057, FR-058 and research R-005: GET /merchants. */
class MerchantsIT : AbstractIntegrationTest() {
    private val base = "/api/v1/merchants"

    @Test
    fun `spellings group together, and the latest spending gives the name, category and account`() {
        val categories = categoryIds(alice)
        val amex = createAccount(alice, "Amex")
        val visa = createAccount(alice, "Visa")
        createSpending(alice, categories.getValue("Groceries"), amex, date = "2026-10-01", merchant = "Loblaws")
        createSpending(alice, categories.getValue("Household"), amex, date = "2026-10-20", merchant = "loblaws")
        createSpending(alice, categories.getValue("Health"), visa, date = "2026-10-20", merchant = "LOBLAWS")
        createSpending(alice, categories.getValue("Taxi"), visa, date = "2026-10-25", merchant = "Uber")

        var names = emptyList<String>()
        getJson(alice, base)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].name")
            .value<List<String>> { names = it }
            .jsonPath("$.items[0].spendingCount")
            .isEqualTo(3)
            .jsonPath("$.items[0].lastUsedOn")
            .isEqualTo("2026-10-20")
            .jsonPath("$.items[0].lastCategory.name")
            .isEqualTo("Health")
            .jsonPath("$.items[0].lastAccount.name")
            .isEqualTo("Visa")
            .jsonPath("$.truncated")
            .isEqualTo(false)

        assertThat(names).containsExactly("LOBLAWS", "Uber")
    }

    @Test
    fun `limit caps the list and says it was truncated, and must be 1 to 1000`() {
        val groceries = categoryIds(alice).getValue("Groceries")
        val amex = createAccount(alice, "Amex")
        createSpending(alice, groceries, amex, merchant = "Bakery")
        createSpending(alice, groceries, amex, merchant = "Butcher")

        getJson(alice, "$base?limit=1")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items.length()")
            .isEqualTo(1)
            .jsonPath("$.truncated")
            .isEqualTo(true)
        listOf("0", "1001").forEach { limit ->
            getJson(alice, "$base?limit=$limit")
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("limit")
        }
    }

    @Test
    fun `nothing recorded is an empty list, another user's merchants never show, and no token is 401`() {
        val groceries = categoryIds(alice).getValue("Groceries")
        createSpending(alice, groceries, createAccount(alice, "Amex"), merchant = "Secret Shop")

        getJson(bob, base)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .isEmpty()
            .jsonPath("$.truncated")
            .isEqualTo(false)
        client
            .get()
            .uri(base)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }
}
