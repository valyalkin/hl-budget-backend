package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.concurrent.Executors

/** FR-036, SC-007, research R-007: a retried create never makes a second spending. */
class IdempotencyIT : AbstractIntegrationTest() {
    private lateinit var groceries: String
    private lateinit var amex: String

    @BeforeEach
    fun setUp() {
        groceries = categoryIds(alice).getValue("Groceries")
        amex = createAccount(alice, "Amex")
    }

    private fun post(
        key: String?,
        json: String = spendingJson(groceries, amex),
        token: String = alice,
    ): RestTestClient.ResponseSpec =
        client
            .post()
            .uri("/api/v1/spendings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .apply { if (key != null) header("Idempotency-Key", key) }
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    private fun rows() = count("SELECT count(*) FROM spendings")

    @Test
    fun `a repeat with the same key and body replays the first spending`() {
        var firstId = ""
        var firstLocation = ""
        post("k1")
            .expectStatus()
            .isCreated()
            .expectHeader()
            .value("Location") { firstLocation = it }
            .expectBody()
            .jsonPath("$.id")
            .value<String> { firstId = it }

        post("k1")
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")
            .expectHeader()
            .valueEquals("Location", firstLocation)
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(firstId)
        assertThat(rows()).isEqualTo(1)
    }

    @Test
    fun `ten parallel identical creates make exactly one spending`() {
        val pool = Executors.newFixedThreadPool(10)
        val statuses =
            try {
                (1..10)
                    .map { pool.submit<Int> { post("k2").returnResult(String::class.java).status.value() } }
                    .map { it.get() }
            } finally {
                pool.shutdown()
            }

        assertThat(statuses.count { it == 201 }).isEqualTo(1)
        assertThat(statuses.count { it == 200 }).isEqualTo(9)
        assertThat(rows()).isEqualTo(1)
    }

    @Test
    fun `the same key with a different body is refused`() {
        post("k1").expectStatus().isCreated()

        post("k1", spendingJson(groceries, amex, merchant = "Metro"))
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("idempotency-key-reused")
        assertThat(rows()).isEqualTo(1)
    }

    @Test
    fun `keys are per user`() {
        post("k1").expectStatus().isCreated()
        val bobCategory = categoryIds(bob).getValue("Groceries")
        val bobAccount = createAccount(bob, "Cash")

        post("k1", spendingJson(bobCategory, bobAccount), token = bob).expectStatus().isCreated()
        assertThat(rows()).isEqualTo(2)
    }

    @Test
    fun `a key that is too long or not visible ASCII is a 422`() {
        post("k".repeat(256)).expectStatus().isEqualTo(422)
        post("has space").expectStatus().isEqualTo(422)
        assertThat(rows()).isZero()
    }

    @Test
    fun `without a key every create is a new spending`() {
        post(null).expectStatus().isCreated()
        post(null).expectStatus().isCreated()
        assertThat(rows()).isEqualTo(2)
    }

    @Test
    fun `deleting the spending frees its key`() {
        var id = ""
        post("k1")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { id = it }
        delete(alice, "/api/v1/spendings/$id").expectStatus().isNoContent()

        post("k1").expectStatus().isCreated()
    }
}
