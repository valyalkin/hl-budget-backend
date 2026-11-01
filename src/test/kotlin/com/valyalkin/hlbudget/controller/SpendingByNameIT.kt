package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.TestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

/**
 * Feature 003, FR-035 and research R-004, as changed by feature 007 (FR-021, FR-026, research
 * R-008): a spending saved with a category or account name. The name must match one of the
 * caller's items; nothing is created.
 */
class SpendingByNameIT : AbstractIntegrationTest() {
    private val base = "/api/v1/spendings"

    private fun byNames(
        category: String,
        account: String,
        date: String = "2026-10-20",
        merchant: String = "Riverside Café",
    ) = """{"date":"$date","merchant":"$merchant","amount":{"amount":"6.75","currency":"CAD"},""" +
        """"categoryName":"$category","accountName":"$account"}"""

    private fun names(
        token: String,
        resource: String,
    ): List<String> {
        var names = emptyList<String>()
        getJson(token, "/api/v1/$resource")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].name")
            .value<List<String>> { names = it }
        return names
    }

    private fun pointers(
        json: String,
        method: String = "POST",
        uri: String = base,
    ): List<String> {
        var pointers = emptyList<String>()
        sendJson(alice, method, uri, json)
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[*].pointer")
            .value<List<String>> { pointers = it }
        return pointers
    }

    @Test
    fun `an unknown category name is refused at the name, and nothing is created`() {
        createAccount(alice, "Amex")

        sendJson(alice, "POST", base, byNames("Groceriess", "Amex"))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:unprocessable-entity")
            .jsonPath("$.errors.length()")
            .isEqualTo(1)
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/categoryName")
            .jsonPath("$.errors[0].field")
            .isEqualTo("categoryName")
            .jsonPath("$.errors[0].code")
            .isEqualTo("NotFound")
            .jsonPath("$.errors[0].message")
            .isEqualTo("is not one of your categories")

        assertThat(names(alice, "categories")).hasSize(17).doesNotContain("Groceriess")
        assertThat(count("select count(*) from spendings")).isZero()
    }

    @Test
    fun `an unknown account name is refused at the name, and nothing is created`() {
        createAccount(alice, "Amex")

        sendJson(alice, "POST", base, byNames("Groceries", "Visa Gold"))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/accountName")
            .jsonPath("$.errors[0].code")
            .isEqualTo("NotFound")
            .jsonPath("$.errors[0].message")
            .isEqualTo("is not one of your accounts")

        assertThat(names(alice, "accounts")).containsExactly("Amex")
    }

    @Test
    fun `both unknown, and other rule breaks, are reported at once`() {
        assertThat(pointers(byNames("Coffee", "Visa"))).containsExactly("#/categoryName", "#/accountName")
        assertThat(pointers(byNames("Coffee", "Visa", date = TestClockConfig.TOMORROW)))
            .containsExactlyInAnyOrder("#/date", "#/categoryName", "#/accountName")

        assertThat(names(alice, "accounts")).isEmpty()
        assertThat(names(alice, "categories")).doesNotContain("Coffee")
    }

    @Test
    fun `a name matching an existing item in any case and spacing reuses it`() {
        val amex = createAccount(alice, "Amex")

        sendJson(alice, "POST", base, byNames("  groceries ", "AMEX"))
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.category.name")
            .isEqualTo("Groceries")
            .jsonPath("$.account.id")
            .isEqualTo(amex)

        assertThat(names(alice, "categories")).hasSize(17)
        assertThat(names(alice, "accounts")).containsExactly("Amex")
    }

    @Test
    fun `exactly one of id and name is required for each, and names have the usual limits`() {
        val amex = createAccount(alice, "Amex")
        val groceries = categoryIds(alice).getValue("Groceries")

        assertThat(
            pointers(
                """{"date":"2026-10-20","merchant":"M","amount":{"amount":"1.00","currency":"CAD"},""" +
                    """"categoryId":"$groceries","categoryName":"Groceries"}""",
            ),
        ).containsExactly("#/categoryId", "#/accountId")
        assertThat(pointers(byNames("a".repeat(51), "Amex"))).containsExactly("#/categoryName")
        assertThat(pointers(byNames("  ", "  "))).containsExactly("#/categoryName", "#/accountName")
        sendJson(alice, "POST", base, spendingJson(groceries, amex)).expectStatus().isCreated()
    }

    @Test
    fun `an update to an unknown name is refused and the spending is unchanged`() {
        val amex = createAccount(alice, "Amex")
        val id = createSpending(alice, categoryIds(alice).getValue("Groceries"), amex)

        assertThat(pointers(byNames("Gifts", "Cash"), method = "PUT", uri = "$base/$id"))
            .containsExactly("#/categoryName", "#/accountName")

        getJson(alice, "$base/$id")
            .expectBody()
            .jsonPath("$.category.name")
            .isEqualTo("Groceries")
            .jsonPath("$.account.name")
            .isEqualTo("Amex")
        assertThat(names(alice, "accounts")).containsExactly("Amex")
    }

    @Test
    fun `an update can move a spending to an existing category by name`() {
        val amex = createAccount(alice, "Amex")
        val id = createSpending(alice, categoryIds(alice).getValue("Groceries"), amex)

        sendJson(alice, "PUT", "$base/$id", byNames("taxi", "amex"))
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.category.name")
            .isEqualTo("Taxi")
    }

    @Test
    fun `a replay by name returns the first spending`() {
        createAccount(alice, "Visa")

        fun post() =
            client
                .post()
                .uri(base)
                .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .header("Idempotency-Key", "by-name-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(byNames("Groceries", "Visa"))
                .exchange()

        post().expectStatus().isCreated()
        post()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")

        assertThat(count("select count(*) from spendings")).isEqualTo(1)
    }

    @Test
    fun `another user's item name does not count`() {
        sendJson(bob, "POST", "/api/v1/categories", """{"name":"Coffee"}""").expectStatus().isCreated()
        createAccount(bob, "Visa")
        createAccount(alice, "Amex")

        assertThat(pointers(byNames("Coffee", "Visa"))).containsExactly("#/categoryName", "#/accountName")
    }
}
