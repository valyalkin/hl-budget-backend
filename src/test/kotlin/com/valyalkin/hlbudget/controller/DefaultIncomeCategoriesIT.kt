package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

/** Feature 009, FR-020, SC-002: every user gets Salary and Investments on first use, exactly once. */
class DefaultIncomeCategoriesIT : AbstractIntegrationTest() {
    private fun incomeCategories(token: String): List<Map<String, Any>> {
        var items = emptyList<Map<String, Any>>()
        getJson(token, "/api/v1/income-categories")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .value<List<Map<String, Any>>> { items = it }
        return items
    }

    private fun incomeCategoriesInDb(subject: String): Int =
        count(
            "SELECT count(*) FROM income_categories c JOIN users u ON u.id = c.user_id WHERE u.auth0_subject = ?",
            subject,
        )

    @Test
    fun `a new user gets Salary and Investments with their fixed colours, sorted by name`() {
        val items = incomeCategories(alice)

        assertThat(items.map { it["name"] }).containsExactly("Investments", "Salary")
        assertThat(items.map { it["color"] }).containsExactly("#1d4ed8", "#1e5b45")
        assertThat(items).allSatisfy { item ->
            assertThat(item["isDefault"]).isEqualTo(true)
            assertThat(item["incomeCount"]).isEqualTo(0)
            assertThat(item.keys).containsExactlyInAnyOrder("id", "name", "isDefault", "incomeCount", "color")
        }
    }

    @Test
    fun `a user seeded with spending categories before this feature gets them once too`() {
        categoryIds(alice)
        jdbcTemplate.update("UPDATE users SET income_categories_seeded_at = NULL")
        jdbcTemplate.update("DELETE FROM income_categories")

        assertThat(incomeCategories(alice)).hasSize(2)
        assertThat(incomeCategories(alice)).hasSize(2)
        assertThat(incomeCategoriesInDb(ALICE)).isEqualTo(2)
    }

    @Test
    fun `ten parallel first calls still give exactly 2`() {
        val token = alice
        val pool = Executors.newFixedThreadPool(10)
        try {
            val calls = (1..10).map { pool.submit<Int> { incomeCategories(token).size } }
            assertThat(calls.map { it.get() }).containsOnly(2)
        } finally {
            pool.shutdown()
        }

        assertThat(incomeCategoriesInDb(ALICE)).isEqualTo(2)
    }

    @Test
    fun `spending categories are not affected and are never listed as income categories`() {
        val income = incomeCategories(alice).map { it["name"] }

        assertThat(categoryIds(alice)).hasSize(17).doesNotContainKeys("Salary", "Investments")
        assertThat(income).doesNotContain("Groceries")
    }

    @Test
    fun `each user has their own`() {
        val aliceIds = incomeCategories(alice).map { it["id"] }
        val bobIds = incomeCategories(bob).map { it["id"] }

        assertThat(aliceIds).hasSize(2).doesNotContainAnyElementsOf(bobIds)
    }
}
