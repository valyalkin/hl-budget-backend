package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.util.UUID
import java.util.concurrent.Executors

/** US1, FR-010, FR-011, SC-002: every user gets exactly the 17 defaults on first use, once. */
class DefaultCategoriesIT : AbstractIntegrationTest() {
    private val defaults =
        listOf(
            "Rent",
            "Groceries",
            "Public Transport",
            "Car",
            "Household",
            "Health",
            "Beauty",
            "Clothes",
            "Education",
            "Travel",
            "Bills",
            "Subscriptions",
            "Other",
            "Sport",
            "Entertainment",
            "Pet",
            "Taxi",
        )

    private fun categories(token: String): List<Map<String, Any>> {
        var items = emptyList<Map<String, Any>>()
        getJson(token, "/api/v1/categories")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .value<List<Map<String, Any>>> { items = it }
        return items
    }

    private fun categoriesInDb(subject: String): Int =
        count(
            "SELECT count(*) FROM categories c JOIN users u ON u.id = c.user_id WHERE u.auth0_subject = ?",
            subject,
        )

    @Test
    fun `a new user gets exactly the 17 defaults, sorted by name`() {
        val items = categories(alice)

        assertThat(items.map { it["name"] }).containsExactlyElementsOf(defaults.sortedBy { it.lowercase() })
        assertThat(items).allSatisfy { item ->
            assertThat(item["isDefault"]).isEqualTo(true)
            assertThat(item["spendingCount"]).isEqualTo(0)
            assertThat(UUID.fromString(item["id"] as String)).isNotNull()
            assertThat(item.keys).containsExactlyInAnyOrder("id", "name", "isDefault", "spendingCount")
        }
    }

    @Test
    fun `asking again does not add them a second time`() {
        val first = categories(alice).map { it["id"] }
        val second = categories(alice).map { it["id"] }

        assertThat(second).isEqualTo(first)
        assertThat(categoriesInDb(ALICE)).isEqualTo(17)
    }

    @Test
    fun `a user who signed in before this feature gets the defaults on first use`() {
        client
            .put()
            .uri("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $alice")
            .exchange()
            .expectStatus()
            .isCreated()
        assertThat(categoriesInDb(ALICE)).isZero()

        assertThat(categories(alice)).hasSize(17)
    }

    @Test
    fun `ten parallel first calls still give exactly 17`() {
        val token = alice
        val pool = Executors.newFixedThreadPool(10)
        try {
            val calls = (1..10).map { pool.submit<Int> { categories(token).size } }
            assertThat(calls.map { it.get() }).containsOnly(17)
        } finally {
            pool.shutdown()
        }

        assertThat(categoriesInDb(ALICE)).isEqualTo(17)
        assertThat(userCount(ALICE)).isEqualTo(1)
    }

    @Test
    fun `each user has their own defaults`() {
        val aliceIds = categories(alice).map { it["id"] }
        val bobIds = categories(bob).map { it["id"] }

        assertThat(aliceIds).hasSize(17).doesNotContainAnyElementsOf(bobIds)
    }

    @Test
    fun `one category can be fetched, but not another user's`() {
        val groceries = categoryIds(alice).getValue("Groceries")

        getJson(alice, "/api/v1/categories/$groceries")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Groceries")
            .jsonPath("$.isDefault")
            .isEqualTo(true)
            .jsonPath("$.spendingCount")
            .isEqualTo(0)

        getJson(bob, "/api/v1/categories/$groceries")
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:not-found")
            .jsonPath("$.details.resource")
            .isEqualTo("category")
        getJson(alice, "/api/v1/categories/${UUID.randomUUID()}").expectStatus().isNotFound()
        getJson(alice, "/api/v1/categories/abc").expectStatus().isBadRequest()
    }

    @Test
    fun `no token is a 401`() {
        client
            .get()
            .uri("/api/v1/categories")
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }
}
