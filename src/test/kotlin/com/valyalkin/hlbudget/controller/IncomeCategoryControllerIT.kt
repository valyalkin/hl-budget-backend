package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Feature 009, US4, FR-021 to FR-025: the caller's income categories. */
class IncomeCategoryControllerIT : AbstractIntegrationTest() {
    private lateinit var defaults: Map<String, String>

    @BeforeEach
    fun seed() {
        defaults = incomeCategoryIds(alice)
    }

    private fun create(name: String) = sendJson(alice, "POST", "/api/v1/income-categories", """{"name":"$name"}""")

    private fun createdId(name: String): String {
        var id = ""
        create(name)
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { id = it }
        return id
    }

    @Test
    fun `a custom income category gets the first unused colour, even with a spending category of the same name`() {
        assertThat(categoryIds(alice)).containsKey("Other")

        create("Other")
            .expectStatus()
            .isCreated()
            .expectHeader()
            .valueMatches("Location", "/api/v1/income-categories/[0-9a-f-]{36}")
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Other")
            .jsonPath("$.isDefault")
            .isEqualTo(false)
            .jsonPath("$.incomeCount")
            .isEqualTo(0)
            .jsonPath("$.color")
            .isEqualTo("#e11d48")
        create("Freelance")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.color")
            .isEqualTo("#0891b2")
    }

    @Test
    fun `names are unique ignoring case, non-empty and at most 50 characters`() {
        create("salary")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("duplicate-name")
        create(" ").expectStatus().isEqualTo(422)
        create("x".repeat(51)).expectStatus().isEqualTo(422)
    }

    @Test
    fun `Salary and Investments cannot be renamed or deleted`() {
        val salary = defaults.getValue("Salary")
        sendJson(alice, "PUT", "/api/v1/income-categories/$salary", """{"name":"Wages"}""")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("default-category")
        delete(alice, "/api/v1/income-categories/${defaults.getValue("Investments")}")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("default-category")
    }

    @Test
    fun `renaming keeps the colour, and a rename to a taken name is refused`() {
        val id = createdId("Other")
        sendJson(alice, "PUT", "/api/v1/income-categories/$id", """{"name":"Gifts"}""")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo("Gifts")
            .jsonPath("$.color")
            .isEqualTo("#e11d48")
        sendJson(alice, "PUT", "/api/v1/income-categories/$id", """{"name":"SALARY"}""")
            .expectStatus()
            .isEqualTo(409)
        getJson(
            alice,
            "/api/v1/income-categories/$id",
        ).expectStatus().isOk().expectBody().jsonPath("$.name").isEqualTo("Gifts")
    }

    @Test
    fun `an unused custom one is deleted, one used by income is refused with the income count`() {
        val unused = createdId("Gifts")
        delete(alice, "/api/v1/income-categories/$unused").expectStatus().isNoContent()
        getJson(alice, "/api/v1/income-categories/$unused").expectStatus().isNotFound()

        val freelance = createdId("Freelance")
        val account = createAccount(alice, "Debit card")
        createIncome(alice, freelance, account)
        delete(alice, "/api/v1/income-categories/$freelance")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("in-use")
            .jsonPath("$.details.incomeCount")
            .isEqualTo(1)
        getJson(alice, "/api/v1/income-categories/$freelance")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.incomeCount")
            .isEqualTo(1)
    }

    @Test
    fun `income categories and spending categories never show in each other's list`() {
        createdId("Freelance")
        sendJson(alice, "POST", "/api/v1/categories", """{"name":"Coffee"}""").expectStatus().isCreated()

        assertThat(categoryIds(alice).keys).doesNotContain("Freelance", "Salary")
        assertThat(incomeCategoryIds(alice).keys).containsExactlyInAnyOrder("Salary", "Investments", "Freelance")
    }
}
