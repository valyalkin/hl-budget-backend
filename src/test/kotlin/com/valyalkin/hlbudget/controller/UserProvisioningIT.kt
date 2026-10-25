package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** SC-002 / research R-004: concurrent first calls for one subject create exactly one row. */
class UserProvisioningIT : AbstractIntegrationTest() {
    @Test
    fun `ten parallel first calls yield one row and exactly one 201`() {
        val subject = "auth0|race-${System.nanoTime()}"
        val token = JwtTestTokens.token(subject = subject)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(CALLS)

        val statuses =
            try {
                val futures =
                    (1..CALLS).map {
                        pool.submit(
                            Callable {
                                start.await()
                                client
                                    .put()
                                    .uri("/api/v1/users/me")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                    .exchange()
                                    .returnResult(String::class.java)
                                    .status
                                    .value()
                            },
                        )
                    }
                start.countDown()
                futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

        assertThat(statuses).allMatch { it in 200..299 }
        assertThat(statuses.count { it == 201 }).isEqualTo(1)
        assertThat(userCount(subject)).isEqualTo(1)
    }

    private companion object {
        const val CALLS = 10
        const val TIMEOUT_SECONDS = 30L
    }
}
