package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/** Feature 009, US5, FR-080, FR-081: many income entries at once, all or nothing. */
class IncomeImportsIT : AbstractIntegrationTest() {
    @BeforeEach
    fun data() {
        incomeCategoryIds(alice)
        createAccount(alice, "Debit card")
    }

    private fun row(
        category: String = "Salary",
        date: String = "2026-10-15",
        amount: String = "5000.00",
        source: String = "Acme Corp",
    ) = """{"date":"$date","source":"$source","amount":{"amount":"$amount","currency":"CAD"},""" +
        """"categoryName":"$category","accountName":"Debit card"}"""

    private fun import(
        rows: List<String>,
        key: String? = null,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/v1/income-imports")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $alice")
            .apply { if (key != null) header("Idempotency-Key", key) }
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"incomes":[${rows.joinToString(",")}]}""")
            .exchange()

    private fun incomeRows(): Int = count("SELECT count(*) FROM incomes")

    @Test
    fun `valid rows become income entries and a receipt`() {
        var id = ""
        import(listOf(row(), row("investments", "2026-10-20", "120.25", "Wealthsimple")))
            .expectStatus()
            .isCreated()
            .expectHeader()
            .valueMatches("Location", "/api/v1/income-imports/[0-9a-f-]{36}")
            .expectBody()
            .jsonPath("$.id")
            .value<String> { id = it }
            .jsonPath("$.incomeCount")
            .isEqualTo(2)
            .jsonPath("$.firstDate")
            .isEqualTo("2026-10-15")
            .jsonPath("$.lastDate")
            .isEqualTo("2026-10-20")
            .jsonPath("$.total.amount")
            .isEqualTo("5120.25")

        assertThat(incomeRows()).isEqualTo(2)
        assertThat(count("SELECT count(*) FROM spendings")).isZero()
        getJson(
            alice,
            "/api/v1/income-imports/$id",
        ).expectStatus().isOk().expectBody().jsonPath("$.incomeCount").isEqualTo(2)
    }

    @Test
    fun `a spending category's name is flagged at its row and nothing is saved`() {
        import(listOf(row(), row("Groceries")))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/incomes/1/categoryName")
            .jsonPath("$.errors[0].message")
            .isEqualTo("is not one of your income categories")

        assertThat(incomeRows()).isZero()
    }

    @Test
    fun `row rules point into the incomes array, and the row count is 1 to 1000`() {
        import(listOf(row(date = "2026-10-28")))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/incomes/0/date")
        import(emptyList()).expectStatus().isEqualTo(422)
        import(List(1001) { row() }).expectStatus().isEqualTo(422)

        assertThat(incomeRows()).isZero()
    }

    @Test
    fun `a repeated key returns the first receipt and adds nothing`() {
        var first = ""
        import(listOf(row()), key = "imp-1")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { first = it }
        import(listOf(row()), key = "imp-1")
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(first)
        import(listOf(row(amount = "1.00")), key = "imp-1")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("idempotency-key-reused")

        assertThat(incomeRows()).isEqualTo(1)
    }

    @Test
    fun `an unknown import is not found`() {
        getJson(alice, "/api/v1/income-imports/9b2f6e1a-4c3d-4e5f-8a7b-0c1d2e3f4a5b").expectStatus().isNotFound()
    }
}
