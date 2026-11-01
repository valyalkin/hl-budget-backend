package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** US2, FR-020 to FR-024: the caller's accounts. */
class AccountControllerIT : AbstractIntegrationTest() {
    private val base = "/api/v1/accounts"

    private fun names(token: String): List<String> {
        var names = emptyList<String>()
        getJson(token, base)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].name")
            .value<List<String>> { names = it }
        return names
    }

    @Test
    fun `a new user starts with no accounts`() {
        getJson(alice, base)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .isEmpty()
    }

    @Test
    fun `create returns 201 with Location and the account`() {
        var id = ""
        sendJson(alice, "POST", base, """{"name":"Amex"}""")
            .expectStatus()
            .isCreated()
            .expectHeader()
            .value("Location") { location -> id = location.substringAfterLast('/') }
            .expectBody()
            .jsonPath("$.id")
            .value<String> { assertThat(it).isEqualTo(id) }
            .jsonPath("$.name")
            .isEqualTo("Amex")
            .jsonPath("$.spendingCount")
            .isEqualTo(0)
        assertThat(UUID.fromString(id)).isNotNull()
        getJson(alice, "$base/$id")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Amex")
    }

    @Test
    fun `invalid names are 422 naming the field, and a body that is not JSON is 400`() {
        fun expectViolation(
            json: String,
            code: String,
        ) = sendJson(alice, "POST", base, json)
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors.length()")
            .isEqualTo(1)
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/name")
            .jsonPath("$.errors[0].code")
            .isEqualTo(code)

        expectViolation("""{"name":"  "}""", "NotBlank")
        expectViolation("""{}""", "Required")
        expectViolation("""{"name":"${"a".repeat(51)}"}""", "Size")
        sendJson(alice, "POST", base, "{").expectStatus().isBadRequest()
    }

    @Test
    fun `names are unique per user ignoring case and surrounding spaces`() {
        createAccount(alice, "Amex")

        sendJson(alice, "POST", base, """{"name":" amex "}""")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:conflict")
            .jsonPath("$.details.reason")
            .isEqualTo("duplicate-name")
        createAccount(bob, "Amex")
    }

    @Test
    fun `the list is sorted by name`() {
        createAccount(alice, "cash")
        createAccount(alice, "Amex")
        createAccount(alice, "Debit card")

        assertThat(names(alice)).containsExactly("Amex", "cash", "Debit card")
    }

    @Test
    fun `rename saves the new name, allows a case change, and refuses another account's name`() {
        val amex = createAccount(alice, "Amex")
        createAccount(alice, "Cash")

        sendJson(alice, "PUT", "$base/$amex", """{"name":"Amex Gold"}""")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Amex Gold")
        sendJson(alice, "PUT", "$base/$amex", """{"name":"AMEX GOLD"}""").expectStatus().isOk()
        sendJson(alice, "PUT", "$base/$amex", """{"name":"cash"}""").expectStatus().isEqualTo(409)
        assertThat(names(alice)).containsExactly("AMEX GOLD", "Cash")
    }

    @Test
    fun `delete removes an unused account, and a second delete is not found`() {
        val cash = createAccount(alice, "Cash")
        createAccount(alice, "Amex") // the last account cannot be deleted (LastAccountIT)

        delete(alice, "$base/$cash").expectStatus().isNoContent()
        delete(alice, "$base/$cash").expectStatus().isNotFound()
        assertThat(names(alice)).containsExactly("Amex")
    }

    @Test
    fun `another user's account does not exist for the caller`() {
        val amex = createAccount(alice, "Amex")

        getJson(bob, "$base/$amex").expectStatus().isNotFound()
        sendJson(bob, "PUT", "$base/$amex", """{"name":"Mine"}""").expectStatus().isNotFound()
        delete(bob, "$base/$amex").expectStatus().isNotFound()
        assertThat(names(bob)).isEmpty()
        assertThat(names(alice)).containsExactly("Amex")
    }

    @Test
    fun `no token is a 401 and a bad id is a 400`() {
        client
            .get()
            .uri(base)
            .exchange()
            .expectStatus()
            .isUnauthorized()
        getJson(alice, "$base/not-a-uuid").expectStatus().isBadRequest()
    }

    private fun color(
        token: String,
        id: String,
    ): String {
        var color = ""
        getJson(token, "$base/$id")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.color")
            .value<String> { color = it }
        return color
    }

    private fun createdColor(
        token: String,
        name: String,
    ): String {
        var color = ""
        sendJson(token, "POST", base, """{"name":"$name"}""")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.color")
            .value<String> { color = it }
        return color
    }

    @Test
    fun `each new account gets the next free colour, kept on rename (feature 008)`() {
        assertThat(listOf("Amex", "Debit card", "Cash").map { createdColor(alice, it) })
            .containsExactly("#e11d48", "#0891b2", "#a855f7")
        var colors = emptyList<String>()
        getJson(alice, base)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].color")
            .value<List<String>> { colors = it }
        // Sorted by name: Amex, Cash, Debit card.
        assertThat(colors).containsExactly("#e11d48", "#a855f7", "#0891b2")

        var amexId = ""
        getJson(alice, base)
            .expectBody()
            .jsonPath("$.items[0].id")
            .value<String> { amexId = it }
        sendJson(alice, "PUT", "$base/$amexId", """{"name":"Amex Gold"}""")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.color")
            .isEqualTo("#e11d48")
        assertThat(color(alice, amexId)).isEqualTo("#e11d48")
    }

    @Test
    fun `a deleted account's colour is free again, and colours are per user (feature 008)`() {
        createAccount(alice, "Amex")
        createAccount(alice, "Debit card")
        val cash = createAccount(alice, "Cash")
        delete(alice, "$base/$cash").expectStatus().isNoContent()

        assertThat(createdColor(alice, "Savings")).isEqualTo("#a855f7")
        assertThat(createdColor(bob, "Bob card")).isEqualTo("#e11d48")
    }
}
