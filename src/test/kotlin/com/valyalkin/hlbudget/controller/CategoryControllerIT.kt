package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** US5, FR-012 to FR-015: the caller's own categories next to the fixed defaults. */
class CategoryControllerIT : AbstractIntegrationTest() {
    private val base = "/api/v1/categories"
    private lateinit var groceries: String

    @BeforeEach
    fun setUp() {
        groceries = categoryIds(alice).getValue("Groceries")
    }

    private fun createCategory(name: String): String {
        var id = ""
        sendJson(alice, "POST", base, """{"name":"$name"}""")
            .expectStatus()
            .isCreated()
            .expectHeader()
            .value("Location") { assertThat(it).startsWith("$base/") }
            .expectBody()
            .jsonPath("$.isDefault")
            .isEqualTo(false)
            .jsonPath("$.spendingCount")
            .isEqualTo(0)
            .jsonPath("$.id")
            .value<String> { id = it }
        return id
    }

    @Test
    fun `a custom category is created and listed next to the defaults`() {
        val coffee = createCategory("Coffee")

        val ids = categoryIds(alice)
        assertThat(ids).hasSize(18).containsEntry("Coffee", coffee)
    }

    @Test
    fun `a default's name, in any case, is already taken`() {
        sendJson(alice, "POST", base, """{"name":"groceries"}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("duplicate-name")
    }

    @Test
    fun `invalid names are 422 at the name`() {
        sendJson(alice, "POST", base, """{"name":""}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/name")
    }

    @Test
    fun `renaming a custom category renames it on its spendings`() {
        val coffee = createCategory("Coffee")
        val amex = createAccount(alice, "Amex")
        val spending = createSpending(alice, coffee, amex)

        sendJson(alice, "PUT", "$base/$coffee", """{"name":"Cafés"}""")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Cafés")
            .jsonPath("$.spendingCount")
            .isEqualTo(1)
        getJson(alice, "/api/v1/spendings/$spending").expectBody().jsonPath("$.category.name").isEqualTo("Cafés")
    }

    @Test
    fun `a default category can be neither renamed nor deleted`() {
        sendJson(alice, "PUT", "$base/$groceries", """{"name":"Food"}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("default-category")
        delete(alice, "$base/$groceries")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("default-category")
        getJson(alice, "$base/$groceries").expectBody().jsonPath("$.name").isEqualTo("Groceries")
    }

    @Test
    fun `an unused custom category is deleted, once`() {
        val gifts = createCategory("Gifts")

        delete(alice, "$base/$gifts").expectStatus().isNoContent()
        delete(alice, "$base/$gifts").expectStatus().isNotFound()
        assertThat(categoryIds(alice)).doesNotContainKey("Gifts").hasSize(17)
    }

    @Test
    fun `another user's custom category does not exist for the caller`() {
        val coffee = createCategory("Coffee")

        getJson(bob, "$base/$coffee").expectStatus().isNotFound()
        sendJson(bob, "PUT", "$base/$coffee", """{"name":"Mine"}""").expectStatus().isNotFound()
        delete(bob, "$base/$coffee").expectStatus().isNotFound()
        assertThat(categoryIds(alice)).containsKey("Coffee")
    }

    @Test
    fun `no token is a 401`() {
        client
            .post()
            .uri(base)
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }
}
