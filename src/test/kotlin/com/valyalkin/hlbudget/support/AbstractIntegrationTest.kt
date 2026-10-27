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
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
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
@Import(TestClockConfig::class)
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var client: RestTestClient

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun resetState() {
        jdbcTemplate.execute(
            "DO $$ BEGIN IF to_regclass('users') IS NOT NULL THEN TRUNCATE users CASCADE; END IF; END $$",
        )
        wireMock.resetAll()
        wireMock.stubFor(get(JwtTestTokens.JWKS_PATH).willReturn(okJson(JwtTestTokens.jwksJson)))
    }

    protected fun bearer(token: String = JwtTestTokens.token()): String = "Bearer $token"

    /** A valid token for [subject]; the two named users make ownership tests read naturally. */
    protected fun tokenFor(subject: String): String = JwtTestTokens.token(subject = subject)

    protected val alice: String get() = tokenFor(ALICE)
    protected val bob: String get() = tokenFor(BOB)

    protected fun RestTestClient.RequestHeadersSpec<*>.auth(token: String) =
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    protected fun getJson(
        token: String,
        uri: String,
    ): RestTestClient.ResponseSpec =
        client
            .get()
            .uri(uri)
            .auth(token)
            .exchange()

    protected fun sendJson(
        token: String,
        method: String,
        uri: String,
        json: String,
    ): RestTestClient.ResponseSpec =
        client
            .method(HttpMethod.valueOf(method))
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .exchange()

    protected fun delete(
        token: String,
        uri: String,
    ): RestTestClient.ResponseSpec =
        client
            .delete()
            .uri(uri)
            .auth(token)
            .exchange()

    /** Creates an account through the API and returns its id. */
    protected fun createAccount(
        token: String,
        name: String,
    ): String = idOf(sendJson(token, "POST", "/api/v1/accounts", """{"name":"$name"}""").expectStatus().isCreated())

    /** The caller's category ids by name (this also seeds the defaults on a first call). */
    protected fun categoryIds(token: String): Map<String, String> {
        var ids = emptyMap<String, String>()
        getJson(token, "/api/v1/categories")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .value<List<Map<String, Any>>> { items ->
                ids =
                    items.associate { it["name"] as String to it["id"] as String }
            }
        return ids
    }

    /** Creates a spending through the API and returns its id. */
    @Suppress("LongParameterList")
    protected fun createSpending(
        token: String,
        categoryId: String,
        accountId: String,
        amount: String = "12.50",
        date: String = "2026-10-20",
        merchant: String = "Loblaws",
    ): String =
        idOf(
            sendJson(
                token,
                "POST",
                "/api/v1/spendings",
                spendingJson(categoryId, accountId, amount, date, merchant),
            ).expectStatus().isCreated(),
        )

    protected fun spendingJson(
        categoryId: String,
        accountId: String,
        amount: String = "12.50",
        date: String = "2026-10-20",
        merchant: String = "Loblaws",
        currency: String = "CAD",
    ): String =
        """{"date":"$date","merchant":"$merchant","amount":{"amount":"$amount","currency":"$currency"},""" +
            """"categoryId":"$categoryId","accountId":"$accountId"}"""

    private fun idOf(response: RestTestClient.ResponseSpec): String {
        var id = ""
        response
            .expectBody()
            .jsonPath("$.id")
            .value<String> { id = it }
        return id
    }

    protected fun count(
        sql: String,
        vararg args: Any,
    ): Int = jdbcTemplate.queryForObject(sql, Int::class.java, *args) ?: 0

    protected fun userCount(subject: String = JwtTestTokens.SUBJECT): Int =
        jdbcTemplate.queryForObject("select count(*) from users where auth0_subject = ?", Int::class.java, subject)
            ?: 0

    companion object {
        const val POSTGRES_IMAGE: String = "postgres:17.7-alpine"
        const val ALICE: String = "auth0|alice"
        const val BOB: String = "auth0|bob"

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
