package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.stream.Stream

/**
 * SC-003 / research R-005, against the real decoder and a WireMock JWKS: every rejection is a
 * traceable 401 problem, and none of them creates a user row.
 */
class SecurityIT : AbstractIntegrationTest() {
    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedTokens")
    fun `an unusable token is a 401 problem and writes nothing`(
        // Only names the case in the test report ({0}).
        @Suppress("UnusedParameter") case: String,
        token: String?,
    ) {
        var requestId: String? = null
        client
            .put()
            .uri("/api/v1/users/me")
            .apply { token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") } }
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectHeader()
            .value(HttpHeaders.CONTENT_TYPE) { assertThat(it).containsIgnoringCase("charset=utf-8") }
            .expectHeader()
            .value(HttpHeaders.WWW_AUTHENTICATE) { assertThat(it).startsWith("Bearer") }
            .expectHeader()
            .value("X-Request-Id") { requestId = it }
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:unauthenticated")
            .jsonPath("$.status")
            .isEqualTo(401)
            .jsonPath("$.traceId")
            .value<String> { assertThat(it).isNotBlank().isEqualTo(requestId) }

        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Int::class.java)).isZero()
    }

    @Test
    fun `a valid token is accepted`() {
        client
            .put()
            .uri("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isCreated()
    }

    @Test
    fun `the probes and the API document are reachable without a token`() {
        listOf("/healthz", "/readyz", "/api/v3/api-docs", "/api/v3/api-docs.yaml").forEach { path ->
            client
                .get()
                .uri(path)
                .exchange()
                .expectStatus()
                .isOk()
        }
    }

    companion object {
        @JvmStatic
        fun rejectedTokens(): Stream<Arguments> =
            Stream.of(
                Arguments.of("no token", null),
                Arguments.of("malformed", JwtTestTokens.MALFORMED),
                Arguments.of("expired", JwtTestTokens.expired()),
                Arguments.of("wrong issuer", JwtTestTokens.wrongIssuer()),
                Arguments.of("wrong audience", JwtTestTokens.wrongAudience()),
                Arguments.of("missing aud", JwtTestTokens.missingAudience()),
                Arguments.of("unknown signing key", JwtTestTokens.signedByUnknownKey()),
                Arguments.of("missing sub", JwtTestTokens.missingSubject()),
                Arguments.of("blank sub", JwtTestTokens.blankSubject()),
            )
    }
}
