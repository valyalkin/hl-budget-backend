package com.valyalkin.hlbudget.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * The real application on a random port, against one shared Postgres 17.7 container, with the
 * Auth0 JWKS served by WireMock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var client: RestTestClient

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun resetState() {
        jdbcTemplate.execute("DO $$ BEGIN IF to_regclass('users') IS NOT NULL THEN TRUNCATE users; END IF; END $$")
        wireMock.resetAll()
        wireMock.stubFor(get(JwtTestTokens.JWKS_PATH).willReturn(okJson(JwtTestTokens.jwksJson)))
    }

    protected fun bearer(token: String = JwtTestTokens.token()): String = "Bearer $token"

    protected fun userCount(subject: String = JwtTestTokens.SUBJECT): Int =
        jdbcTemplate.queryForObject("select count(*) from users where auth0_subject = ?", Int::class.java, subject)
            ?: 0

    companion object {
        const val POSTGRES_IMAGE: String = "postgres:17.7-alpine"

        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName("hl-budget")
                .withUsername("hl-budget")
                .withPassword("test")

        @JvmStatic
        val wireMock: WireMockServer =
            WireMockServer(
                WireMockConfiguration
                    .options()
                    .dynamicPort()
                    // The JDK client tries an h2c upgrade that WireMock's Jetty resets.
                    .http2PlainDisabled(true),
            )

        init {
            postgres.start()
            wireMock.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("hlbudget.auth.jwk-set-uri") { wireMock.baseUrl() + JwtTestTokens.JWKS_PATH }
            registry.add("hlbudget.auth.issuer") { JwtTestTokens.ISSUER }
            registry.add("hlbudget.auth.audience") { JwtTestTokens.AUDIENCE }
            registry.add("hlbudget.auth.claims-namespace") { JwtTestTokens.NAMESPACE }
        }
    }
}
