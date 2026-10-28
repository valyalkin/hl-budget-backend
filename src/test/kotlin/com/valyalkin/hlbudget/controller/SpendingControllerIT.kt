package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.TestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/** US3, FR-030 to FR-035: record, read, correct and delete a spending. */
class SpendingControllerIT : AbstractIntegrationTest() {
    private val base = "/api/v1/spendings"
    private lateinit var groceries: String
    private lateinit var taxi: String
    private lateinit var amex: String

    @BeforeEach
    fun setUp() {
        val ids = categoryIds(alice)
        groceries = ids.getValue("Groceries")
        taxi = ids.getValue("Taxi")
        amex = createAccount(alice, "Amex")
    }

    @Test
    fun `create returns 201 with Location and the spending, names and exact amount included`() {
        var location = ""
        var created = ""
        var updated = ""
        sendJson(alice, "POST", base, spendingJson(groceries, amex, amount = "84.37"))
            .expectStatus()
            .isCreated()
            .expectHeader()
            .value("Location") { location = it }
            .expectBody()
            .jsonPath("$.date")
            .isEqualTo("2026-10-20")
            .jsonPath("$.merchant")
            .isEqualTo("Loblaws")
            .jsonPath("$.amount.amount")
            .isEqualTo("84.37")
            .jsonPath("$.amount.currency")
            .isEqualTo("CAD")
            .jsonPath("$.category.id")
            .isEqualTo(groceries)
            .jsonPath("$.category.name")
            .isEqualTo("Groceries")
            .jsonPath("$.account.name")
            .isEqualTo("Amex")
            .jsonPath("$.createdAt")
            .value<String> { created = it }
            .jsonPath("$.updatedAt")
            .value<String> { updated = it }
        assertThat(location).matches("/api/v1/spendings/[0-9a-f-]{36}")
        assertThat(created).endsWith("Z").isEqualTo(updated)
        assertThat(Instant.parse(created)).isAfterOrEqualTo(TestClockConfig.NOW)

        getJson(alice, location)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.merchant")
            .isEqualTo("Loblaws")
            .jsonPath("$.category.name")
            .isEqualTo("Groceries")
    }

    @Test
    fun `amounts are returned with two decimals and the maximum is accepted`() {
        val id = createSpending(alice, groceries, amex, amount = "84.3")
        getJson(alice, "$base/$id").expectBody().jsonPath("$.amount.amount").isEqualTo("84.30")

        val max = createSpending(alice, groceries, amex, amount = "99999999.99")
        getJson(alice, "$base/$max").expectBody().jsonPath("$.amount.amount").isEqualTo("99999999.99")
    }

    @Test
    fun `update replaces the details and keeps createdAt`() {
        val id = createSpending(alice, groceries, amex)
        var createdAt = ""
        getJson(alice, "$base/$id").expectBody().jsonPath("$.createdAt").value<String> { createdAt = it }

        sendJson(alice, "PUT", "$base/$id", spendingJson(taxi, amex, amount = "23.10", merchant = "Uber"))
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.merchant")
            .isEqualTo("Uber")
            .jsonPath("$.amount.amount")
            .isEqualTo("23.10")
            .jsonPath("$.category.name")
            .isEqualTo("Taxi")
            .jsonPath("$.createdAt")
            .isEqualTo(createdAt)
            .jsonPath("$.updatedAt")
            .value<String> { assertThat(Instant.parse(it)).isAfter(Instant.parse(createdAt)) }
    }

    @Test
    fun `delete removes it for good`() {
        val id = createSpending(alice, groceries, amex)

        delete(alice, "$base/$id").expectStatus().isNoContent()
        getJson(alice, "$base/$id").expectStatus().isNotFound()
        delete(alice, "$base/$id").expectStatus().isNotFound()
    }

    @Test
    fun `every invalid field is reported at once, and nothing is saved`() {
        val json =
            """{"date":"${TestClockConfig.TOMORROW}","amount":{"amount":84.37,"currency":"CAD"},""" +
                """"categoryId":"$groceries","accountId":"$amex"}"""

        sendJson(alice, "POST", base, json)
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:unprocessable-entity")
            .jsonPath("$.errors[0].code")
            .isEqualTo("TypeMismatch")
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/amount/amount")
        assertThat(count("SELECT count(*) FROM spendings")).isZero()
    }

    @Test
    fun `bean validation failures name each field`() {
        fun expectCodes(
            json: String,
            vararg codes: String,
        ) {
            var found = emptyList<String>()
            sendJson(alice, "POST", base, json)
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors[*].code")
                .value<List<String>> { found = it }
            assertThat(found).containsExactlyInAnyOrder(*codes)
        }

        expectCodes(spendingJson(groceries, amex, amount = "1.234"), "Pattern")
        expectCodes(spendingJson(groceries, amex, amount = "100000000.00"), "Pattern")
        expectCodes(spendingJson(groceries, amex, merchant = "m".repeat(256)), "Size")
        expectCodes(spendingJson(groceries, amex, merchant = "   "), "NotBlank")
        expectCodes(
            """{"date":"2026-10-20","merchant":"x","categoryId":"$groceries","accountId":"$amex"}""",
            "Required",
        )
    }

    @Test
    fun `rules only the service can check are 422 at the right pointer`() {
        var pointers = emptyList<String>()
        sendJson(
            alice,
            "POST",
            base,
            spendingJson(groceries, amex, amount = "0", date = TestClockConfig.TOMORROW, currency = "EUR"),
        ).expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[*].pointer")
            .value<List<String>> { pointers = it }

        assertThat(pointers).containsExactly("#/date", "#/amount/amount", "#/amount/currency")
        sendJson(alice, "POST", base, spendingJson(groceries, amex, date = TestClockConfig.TODAY))
            .expectStatus()
            .isCreated()
    }

    @Test
    fun `another user's account or category cannot be referenced`() {
        val bobAccount = createAccount(bob, "Cash")
        val bobCategory = categoryIds(bob).getValue("Taxi")

        sendJson(alice, "POST", base, spendingJson(bobCategory, bobAccount))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[0].pointer")
            .isEqualTo("#/categoryId")
            .jsonPath("$.errors[0].code")
            .isEqualTo("NotFound")
            .jsonPath("$.errors[1].pointer")
            .isEqualTo("#/accountId")
    }

    @Test
    fun `another user's spending does not exist for the caller`() {
        val id = createSpending(alice, groceries, amex)
        val bobAccount = createAccount(bob, "Cash")
        val bobCategory = categoryIds(bob).getValue("Taxi")

        getJson(bob, "$base/$id").expectStatus().isNotFound()
        sendJson(bob, "PUT", "$base/$id", spendingJson(bobCategory, bobAccount)).expectStatus().isNotFound()
        delete(bob, "$base/$id").expectStatus().isNotFound()
        getJson(alice, "$base/$id").expectStatus().isOk()
    }
}
