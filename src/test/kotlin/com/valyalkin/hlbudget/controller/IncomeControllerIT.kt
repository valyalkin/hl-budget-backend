package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/** Feature 009, US1, US2, FR-010 to FR-013: the caller's income entries, the twin of the spending ITs. */
class IncomeControllerIT : AbstractIntegrationTest() {
    private lateinit var incomeCategories: Map<String, String>
    private lateinit var debit: String

    @BeforeEach
    fun data() {
        incomeCategories = incomeCategoryIds(alice)
        debit = createAccount(alice, "Debit card")
    }

    private val salary get() = incomeCategories.getValue("Salary")

    private fun post(
        json: String,
        key: String? = null,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/v1/incomes")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $alice")
            .apply { if (key != null) header("Idempotency-Key", key) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    private fun byNames(
        category: String,
        account: String = "Debit card",
        source: String = "Acme Corp",
        amount: String = "5000.00",
        date: String = "2026-10-15",
    ) = """{"date":"$date","source":"$source","amount":{"amount":"$amount","currency":"CAD"},""" +
        """"categoryName":"$category","accountName":"$account"}"""

    private fun incomeRows(): Int = count("SELECT count(*) FROM incomes")

    @Test
    fun `create by ids returns the income with names, in CAD, at its Location`() {
        post(incomeJson(salary, debit))
            .expectStatus()
            .isCreated()
            .expectHeader()
            .valueMatches("Location", "/api/v1/incomes/[0-9a-f-]{36}")
            .expectBody()
            .jsonPath("$.source")
            .isEqualTo("Acme Corp")
            .jsonPath("$.date")
            .isEqualTo("2026-10-15")
            .jsonPath("$.amount.amount")
            .isEqualTo("5000.00")
            .jsonPath("$.amount.currency")
            .isEqualTo("CAD")
            .jsonPath("$.category.name")
            .isEqualTo("Salary")
            .jsonPath("$.account.name")
            .isEqualTo("Debit card")
    }

    @Test
    fun `create by names matches income categories and accounts ignoring case and spaces`() {
        post(byNames(" salary ", account = "DEBIT CARD"))
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.category.id")
            .isEqualTo(salary)
            .jsonPath("$.account.id")
            .isEqualTo(debit)
    }

    @Test
    fun `a spending category's name is not one of your income categories, and nothing is created`() {
        post(byNames("Groceries"))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/categoryName")
            .jsonPath("$.errors[0].code")
            .isEqualTo("NotFound")
            .jsonPath("$.errors[0].message")
            .isEqualTo("is not one of your income categories")
        val groceries = categoryIds(alice).getValue("Groceries")
        post(incomeJson(groceries, debit))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/categoryId")

        assertThat(incomeRows()).isZero()
        assertThat(incomeCategoryIds(alice)).hasSize(2)
    }

    @Test
    fun `an unknown account and a missing pair are reported at their fields`() {
        post(byNames("Salary", account = "Wallet"))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/accountName")
            .jsonPath("$.errors[0].message")
            .isEqualTo("is not one of your accounts")
        post("""{"date":"2026-10-15","source":"Acme","amount":{"amount":"1.00","currency":"CAD"}}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[*].code")
            .value<List<String>> { assertThat(it).containsOnly("ExactlyOne") }
    }

    @Test
    fun `date, amount and source follow the spending rules, all reported at once`() {
        post(byNames("Salary", date = "2026-10-28", amount = "0", source = " "))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[*].pointer")
            .value<List<String>> { assertThat(it).contains("#/source") }
        post(byNames("Salary", date = "2026-10-28", amount = "0"))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[*].pointer")
            .value<List<String>> { assertThat(it).containsExactlyInAnyOrder("#/date", "#/amount/amount") }
        post(byNames("Salary", amount = "1.234")).expectStatus().isEqualTo(422)
        post(byNames("Salary", source = "x".repeat(256))).expectStatus().isEqualTo(422)

        assertThat(incomeRows()).isZero()
    }

    @Test
    fun `the same Idempotency-Key creates one income, a different body with it is refused`() {
        var firstId = ""
        post(byNames("Salary"), key = "k1")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { firstId = it }
        post(byNames("salary"), key = "k1")
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(firstId)
        post(byNames("Salary", amount = "1.00"), key = "k1")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("idempotency-key-reused")

        assertThat(incomeRows()).isEqualTo(1)
    }

    @Test
    fun `get, replace and delete one income`() {
        val id = createIncome(alice, salary, debit)
        val investments = incomeCategories.getValue("Investments")

        getJson(alice, "/api/v1/incomes/$id")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(id)
        sendJson(
            alice,
            "PUT",
            "/api/v1/incomes/$id",
            incomeJson(investments, debit, amount = "120.25", source = "Broker"),
        ).expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.category.name")
            .isEqualTo("Investments")
            .jsonPath("$.source")
            .isEqualTo("Broker")
            .jsonPath("$.amount.amount")
            .isEqualTo("120.25")
        sendJson(alice, "PUT", "/api/v1/incomes/$id", byNames("Groceries")).expectStatus().isEqualTo(422)
        delete(alice, "/api/v1/incomes/$id").expectStatus().isNoContent()
        getJson(alice, "/api/v1/incomes/$id").expectStatus().isNotFound()
        delete(alice, "/api/v1/incomes/$id").expectStatus().isNotFound()
    }

    @Test
    fun `the list is paged, filtered and sorted like spendings`() {
        val investments = incomeCategories.getValue("Investments")
        val brokerage = createAccount(alice, "Brokerage")
        createIncome(alice, salary, debit, amount = "5000.00", date = "2026-09-15")
        createIncome(alice, salary, debit, amount = "5000.00", date = "2026-10-15")
        createIncome(alice, investments, brokerage, amount = "120.25", date = "2026-10-20")
        createIncome(alice, investments, brokerage, amount = "200.25", date = "2026-10-05")

        var dates = emptyList<String>()
        getJson(alice, "/api/v1/incomes?from=2026-10-01&to=2026-10-31&size=2")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].date")
            .value<List<String>> { dates = it }
            .jsonPath("$.totalItems")
            .isEqualTo(3)
            .jsonPath("$.totalPages")
            .isEqualTo(2)
        assertThat(dates).containsExactly("2026-10-20", "2026-10-15")

        var amounts = emptyList<String>()
        getJson(alice, "/api/v1/incomes?categoryId=$investments&accountId=$brokerage&sort=amount,asc")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items[*].amount.amount")
            .value<List<String>> { amounts = it }
        assertThat(amounts).containsExactly("120.25", "200.25")

        getJson(alice, "/api/v1/incomes?sort=name").expectStatus().isEqualTo(422)
        getJson(alice, "/api/v1/incomes?from=2026-11-01&to=2026-10-01").expectStatus().isEqualTo(422)
        getJson(alice, "/api/v1/incomes?size=101").expectStatus().isEqualTo(422)
    }

    @Test
    fun `a spending cannot use an income category, and spendings never list income`() {
        createIncome(alice, salary, debit)
        sendJson(
            alice,
            "POST",
            "/api/v1/spendings",
            """{"date":"2026-10-15","merchant":"Shop","amount":{"amount":"1.00","currency":"CAD"},""" +
                """"categoryName":"Salary","accountName":"Debit card"}""",
        ).expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].message")
            .isEqualTo("is not one of your categories")

        getJson(alice, "/api/v1/spendings")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.totalItems")
            .isEqualTo(0)
    }
}
