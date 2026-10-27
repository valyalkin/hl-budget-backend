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

    private fun RestTestClient.ResponseSpec.expectViolation(
        pointer: String,
        code: String,
    ) = expectProblem(422, "unprocessable-entity")
        .jsonPath("$.errors[0].pointer")
        .isEqualTo(pointer)
        .jsonPath("$.errors[0].code")
        .isEqualTo(code)

    private fun postBody(json: String) =
        client
            .post()
            .uri("/api/v1/test-failures/body")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    @Test
    fun `a body constraint failure is a 422 naming the field without echoing the value`() {
        postBody("""{"name":"   ","count":1}""")
            .expectViolation("#/name", "NotBlank")
            .consumeWith { result -> assertThat(String(result.responseBody ?: ByteArray(0))).doesNotContain("   \"") }
    }

    @Test
    fun `a missing body field is a 422 Required`() {
        postBody("""{"count":1}""").expectViolation("#/name", "Required")
    }

    @Test
    fun `a wrongly typed body field is a 422 TypeMismatch`() {
        postBody("""{"name":"x","count":"seven"}""").expectViolation("#/count", "TypeMismatch")
    }

    @Test
    fun `a body that is not JSON is a 400 with no errors`() {
        postBody("{")
            .expectProblem(400, "bad-request")
            .jsonPath("$.errors")
            .doesNotExist()
    }

    @Test
    fun `query parameter failures are 422`() {
        fun get(uri: String) =
            client
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .exchange()
        get("/api/v1/test-failures/query?size=11").expectViolation("#/size", "Max")
        get("/api/v1/test-failures/query?size=abc").expectViolation("#/size", "TypeMismatch")
        get("/api/v1/test-failures/query").expectViolation("#/size", "Required")
    }

    @Test
    fun `header failures are 422`() {
        client
            .get()
            .uri("/api/v1/test-failures/header")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .header("X-Thing", "toolong")
            .exchange()
            .expectProblem(422, "unprocessable-entity")
            .jsonPath("$.errors[0].code")
            .isEqualTo("Size")
        client
            .get()
            .uri("/api/v1/test-failures/header")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectViolation("#/X-Thing", "Required")
    }

    @Test
    fun `a path id that is not a UUID is a 400 with no errors`() {
        client
            .get()
            .uri("/api/v1/test-failures/things/abc")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectProblem(400, "bad-request")
            .jsonPath("$.errors")
            .doesNotExist()
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
