package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.TestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Feature 004 (specs/004-csv-import/contracts/openapi.yaml): many spendings at once, all or
 * nothing, retry-safe with an Idempotency-Key, and read back through `Location`.
 */
class SpendingImportsIT : AbstractIntegrationTest() {
    private val base = "/api/v1/spending-imports"

    private fun row(
        date: String = "2026-10-20",
        merchant: String = "Corner Bakery",
        amount: String = "12.40",
        category: String = "Groceries",
        account: String = "Visa",
    ) = """{"date":"$date","merchant":"$merchant","amount":{"amount":"$amount","currency":"CAD"},""" +
        """"categoryName":"$category","accountName":"$account"}"""

    private fun body(vararg rows: String) = """{"spendings":[${rows.joinToString(",")}]}"""

    private val twoRows =
        body(
            row(date = TestClockConfig.TODAY),
            row(
                date = "2026-09-30",
                merchant = "Lakeshore Fuel, Hwy 7",
                amount = "61.20",
                category = "car",
                account = "Amex",
            ),
        )

    private fun post(
        json: String,
        key: String? = null,
        token: String = alice,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri(base)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .apply { if (key != null) header("Idempotency-Key", key) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    private fun rows(table: String) = count("SELECT count(*) FROM $table")

    private fun pointers(response: RestTestClient.ResponseSpec): List<String> {
        var pointers = emptyList<String>()
        response
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors[*].pointer")
            .value<List<String>> { pointers = it }
        return pointers
    }

    @Test
    fun `an import creates every row and answers with its receipt`() {
        categoryIds(alice)
        var location = ""
        post(twoRows, key = "k1")
            .expectStatus()
            .isCreated()
            .expectHeader()
            .value("Location") { location = it }
            .expectBody()
            .jsonPath("$.spendingCount")
            .isEqualTo(2)
            .jsonPath("$.firstDate")
            .isEqualTo("2026-09-30")
            .jsonPath("$.lastDate")
            .isEqualTo(TestClockConfig.TODAY)
            .jsonPath("$.total.amount")
            .isEqualTo("73.60")
            .jsonPath("$.total.currency")
            .isEqualTo("CAD")
            .jsonPath("$.createdAt")
            .exists()

        assertThat(location).matches("/api/v1/spending-imports/[0-9a-f-]{36}")
        assertThat(rows("spendings")).isEqualTo(2)
        // "car" reuses the default Car; Visa and Amex are new, once each.
        assertThat(count("SELECT count(*) FROM categories WHERE lower(name) = 'car'")).isEqualTo(1)
        assertThat(count("SELECT count(*) FROM categories")).isEqualTo(17)
        assertThat(count("SELECT count(*) FROM accounts")).isEqualTo(2)
        assertThat(count("SELECT count(*) FROM spendings WHERE merchant = 'Lakeshore Fuel, Hwy 7'")).isEqualTo(1)

        getJson(alice, location)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.spendingCount")
            .isEqualTo(2)
            .jsonPath("$.total.amount")
            .isEqualTo("73.60")
    }

    @Test
    fun `identical rows in one file become separate spendings`() {
        post(body(row(), row())).expectStatus().isCreated()

        assertThat(rows("spendings")).isEqualTo(2)
    }

    @Test
    fun `a repeat with the same key and rows replays the first import`() {
        var firstId = ""
        post(twoRows, key = "k1").expectStatus().isCreated().expectBody().jsonPath("$.id").value<String> {
            firstId = it
        }

        post(twoRows, key = "k1")
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")
            .expectHeader()
            .valueEquals("Location", "$base/$firstId")
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(firstId)

        assertThat(rows("spendings")).isEqualTo(2)
        assertThat(rows("spending_imports")).isEqualTo(1)
    }

    @Test
    fun `the same key with different rows is refused and creates nothing`() {
        post(twoRows, key = "k1").expectStatus().isCreated()

        post(body(row(amount = "1.00")), key = "k1")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("idempotency-key-reused")

        assertThat(rows("spendings")).isEqualTo(2)
    }

    @Test
    fun `one invalid row refuses the whole import, and nothing is created`() {
        categoryIds(alice)
        val categories = rows("categories")

        val found =
            pointers(
                post(
                    body(
                        row(category = "Brand New Category"),
                        row(date = TestClockConfig.TOMORROW),
                        row(amount = "0", account = "Brand New Account"),
                    ),
                ),
            )

        assertThat(found).containsExactly("#/spendings/1/date", "#/spendings/2/amount/amount")
        assertThat(rows("spendings")).isZero()
        assertThat(rows("categories")).isEqualTo(categories)
        assertThat(rows("accounts")).isZero()
        assertThat(rows("spending_imports")).isZero()
    }

    @Test
    fun `field limits are reported per row`() {
        assertThat(pointers(post(body(row(), row(merchant = " "))))).containsExactly("#/spendings/1/merchant")
        assertThat(pointers(post(body(row(category = "c".repeat(51))))))
            .containsExactly("#/spendings/0/categoryName")
        assertThat(pointers(post(body(row(account = "")))))
            .containsExactly("#/spendings/0/accountName")
    }

    @Test
    fun `an import needs 1 to 1000 rows`() {
        assertThat(pointers(post(body()))).containsExactly("#/spendings")
        assertThat(pointers(post(body(*Array(1001) { row() })))).containsExactly("#/spendings")
        assertThat(rows("spendings")).isZero()
    }

    @Test
    fun `malformed JSON is a bad request`() {
        post("""{"spendings":[""").expectStatus().isBadRequest()
    }

    @Test
    fun `importing needs a token`() {
        client
            .post()
            .uri(base)
            .contentType(MediaType.APPLICATION_JSON)
            .body(twoRows)
            .exchange()
            .expectStatus()
            .isUnauthorized()
        assertThat(rows("spendings")).isZero()
    }

    @Test
    fun `an unknown import is not found`() {
        getJson(alice, "$base/9b2f6e1a-4c3d-4e5f-8a7b-0c1d2e3f4a5b")
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$.details.resource")
            .isEqualTo("spending-import")
    }
}
