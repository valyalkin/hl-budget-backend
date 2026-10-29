package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.TestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Feature 003, FR-035 and research R-004: a spending saved with a category or account name. */
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
    fun `new names are created with the spending, and the response names them`() {
        sendJson(alice, "POST", base, byNames("Coffee", "Visa"))
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.category.name")
            .isEqualTo("Coffee")
            .jsonPath("$.account.name")
            .isEqualTo("Visa")

        assertThat(names(alice, "categories")).contains("Coffee")
        assertThat(names(alice, "accounts")).containsExactly("Visa")
        assertThat(count("select count(*) from categories where name = 'Coffee' and is_default = false"))
            .isEqualTo(1)
    }

    @Test
    fun `a name matching an existing item in any case reuses it`() {
        val amex = createAccount(alice, "Amex")

        sendJson(alice, "POST", base, byNames(" GROCERIES ", "amex"))
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
    fun `a refused spending creates nothing`() {
        assertThat(pointers(byNames("Brand new", "Brand new", date = TestClockConfig.TOMORROW)))
            .containsExactly("#/date")

        assertThat(names(alice, "accounts")).isEmpty()
        assertThat(names(alice, "categories")).doesNotContain("Brand new")
    }

    @Test
    fun `an update can name a new category and account`() {
        val amex = createAccount(alice, "Amex")
        val id = createSpending(alice, categoryIds(alice).getValue("Groceries"), amex)

        sendJson(alice, "PUT", "$base/$id", byNames("Gifts", "Cash"))
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.category.name")
            .isEqualTo("Gifts")
            .jsonPath("$.account.name")
            .isEqualTo("Cash")
        assertThat(names(alice, "accounts")).containsExactly("Amex", "Cash")
    }

    @Test
    fun `a replay by name returns the first spending and creates one category`() {
        fun post() =
            client
                .post()
                .uri(base)
                .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                .header("Idempotency-Key", "by-name-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body(byNames("Coffee", "Visa"))
                .exchange()

        post().expectStatus().isCreated()
        post()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")

        assertThat(count("select count(*) from spendings")).isEqualTo(1)
        assertThat(count("select count(*) from categories where lower(name) = 'coffee'")).isEqualTo(1)
    }

    @Test
    fun `concurrent saves naming the same new category share one category`() {
        categoryIds(alice) // seed the defaults first, so the race is only about "Coffee"
        val pool = Executors.newFixedThreadPool(4)
        val tasks =
            (1..4).map { n ->
                Callable {
                    sendJson(alice, "POST", base, byNames("Coffee", "Visa", merchant = "Shop $n"))
                        .expectStatus()
                        .isCreated()
                }
            }
        pool.invokeAll(tasks).forEach { it.get() }
        pool.shutdown()

        assertThat(count("select count(*) from spendings")).isEqualTo(4)
        assertThat(count("select count(*) from categories where lower(name) = 'coffee'")).isEqualTo(1)
        assertThat(count("select count(*) from accounts where lower(name) = 'visa'")).isEqualTo(1)
    }
}
