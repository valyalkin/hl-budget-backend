package com.valyalkin.hlbudget.error

import com.valyalkin.failures.FailureController
import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

@Import(GlobalExceptionHandlerIT.FailureEndpoints::class)
class GlobalExceptionHandlerIT : AbstractIntegrationTest() {
    @TestConfiguration
    class FailureEndpoints {
        @Bean
        fun failureController(): FailureController = FailureController()
    }

    private fun RestTestClient.ResponseSpec.expectProblem(
        status: Int,
        category: String,
    ): RestTestClient.BodyContentSpec {
        var header: String? = null
        val body =
            expectStatus()
                .isEqualTo(status)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader()
                .value("X-Request-Id") { header = it }
                .expectBody()
        return body
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:$category")
            .jsonPath("$.status")
            .isEqualTo(status)
            .jsonPath("$.title")
            .exists()
            .jsonPath("$.instance")
            .exists()
            .jsonPath("$.timestamp")
            .exists()
            .jsonPath("$.traceId")
            .value<String> { assertThat(it).isNotBlank().isEqualTo(header) }
    }

    @Test
    fun `an unknown path is a problem whose traceId equals the X-Request-Id header`() {
        client
            .get()
            .uri("/api/v1/nope")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(404, "not-found")
    }

    @Test
    fun `an unauthenticated request is an unauthenticated problem`() {
        client
            .get()
            .uri("/api/v1/nope")
            .exchange()
            .expectProblem(401, "unauthenticated")
    }

    @Test
    fun `an unexpected exception is a 500 that leaks nothing`() {
        client
            .get()
            .uri("/api/v1/test-failures/unexpected")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(500, "internal-error")
            .jsonPath("$.detail")
            .isEqualTo("An unexpected error occurred")
            .jsonPath("$.details")
            .doesNotExist()
            .consumeWith { result ->
                val raw = String(result.responseBody ?: ByteArray(0))
                assertThat(raw).doesNotContain("secret_internal_name").doesNotContain("IllegalState")
            }
    }

    @Test
    fun `a supplied X-Request-Id is echoed in the header and the body`() {
        client
            .get()
            .uri("/api/v1/nope")
            .header("X-Request-Id", "caller-trace-7")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .valueEquals("X-Request-Id", "caller-trace-7")
            .expectBody()
            .jsonPath("$.traceId")
            .isEqualTo("caller-trace-7")
    }

    @Test
    fun `an unsafe X-Request-Id is replaced with a generated one`() {
        client
            .get()
            .uri("/api/v1/nope")
            .header("X-Request-Id", "bad id\twith spaces")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectHeader()
            .value("X-Request-Id") { assertThat(it).matches("[0-9a-f]{16}") }
    }

    @Test
    fun `the wrong method is a bad-request category problem`() {
        client
            .delete()
            .uri("/api/v1/test-failures/unexpected")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(405, "bad-request")
    }
}
