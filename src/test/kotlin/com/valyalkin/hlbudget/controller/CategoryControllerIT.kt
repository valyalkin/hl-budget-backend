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

    private fun createCategory(name: String): String = created(name).first

    /** Creates a category and returns its id and colour. */
    private fun created(name: String): Pair<String, String> {
        var id = ""
        var color = ""
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
            .jsonPath("$.color")
            .value<String> { color = it }
        return id to color
    }

    private fun colorOf(id: String): String {
        var color = ""
        getJson(alice, "$base/$id")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.color")
            .value<String> { color = it }
        return color
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
    fun `new categories get the spare colours in order and keep them after a rename`() {
        val (coffee, coffeeColor) = created("Coffee")
        val (_, teaColor) = created("Tea")

        assertThat(coffeeColor).isEqualTo("#e11d48")
        assertThat(teaColor).isEqualTo("#0891b2")
        sendJson(alice, "PUT", "$base/$coffee", """{"name":"Espresso"}""")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.color")
            .isEqualTo("#e11d48")
        assertThat(colorOf(coffee)).isEqualTo("#e11d48")
    }

    @Test
    fun `a deleted category's colour goes to the next new one`() {
        val (gifts, giftsColor) = created("Gifts")
        created("Books")
        delete(alice, "$base/$gifts").expectStatus().isNoContent()

        assertThat(created("Music").second).isEqualTo(giftsColor)
    }

    @Test
    fun `colours are assigned per user`() {
        created("Coffee")
        var bobColor = ""
        sendJson(bob, "POST", base, """{"name":"Coffee"}""")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.color")
            .value<String> { bobColor = it }

        assertThat(bobColor).isEqualTo("#e11d48")
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
