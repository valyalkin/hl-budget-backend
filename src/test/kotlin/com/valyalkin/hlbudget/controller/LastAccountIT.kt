package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Feature 003, FR-084 and FR-086: the caller's only account cannot be deleted. */
class LastAccountIT : AbstractIntegrationTest() {
    private val base = "/api/v1/accounts"

    @Test
    fun `the only account is refused with 422 last-account and is kept`() {
        val cash = createAccount(alice, "Cash")

        delete(alice, "$base/$cash")
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:unprocessable-entity")
            .jsonPath("$.details.reason")
            .isEqualTo("last-account")
        getJson(alice, "$base/$cash").expectStatus().isOk()
    }

    @Test
    fun `with two unused accounts the first goes and the second stays`() {
        val cash = createAccount(alice, "Cash")
        val amex = createAccount(alice, "Amex")

        delete(alice, "$base/$cash").expectStatus().isNoContent()
        delete(alice, "$base/$amex").expectStatus().isEqualTo(422)
    }

    @Test
    fun `in use is reported before last account, and another user's account is not found first`() {
        val amex = createAccount(alice, "Amex")
        createSpending(alice, categoryIds(alice).getValue("Groceries"), amex)

        delete(alice, "$base/$amex")
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.details.reason")
            .isEqualTo("in-use")
        val bobs = createAccount(bob, "Only")
        delete(alice, "$base/$bobs").expectStatus().isNotFound()
    }

    @Test
    fun `two concurrent deletes of the last two accounts leave exactly one`() {
        val first = createAccount(alice, "First")
        val second = createAccount(alice, "Second")
        val pool = Executors.newFixedThreadPool(2)
        val statuses =
            pool
                .invokeAll(
                    listOf(first, second).map { id ->
                        Callable {
                            var status = 0
                            delete(alice, "$base/$id")
                                .expectStatus()
                                .value { status = it }
                            status
                        }
                    },
                ).map { it.get() }
        pool.shutdown()

        assertThat(statuses).containsExactlyInAnyOrder(204, 422)
        assertThat(count("select count(*) from accounts")).isEqualTo(1)
    }
}
