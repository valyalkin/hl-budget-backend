package com.valyalkin.hlbudget.error

import com.valyalkin.hlbudget.model.UpsertResult
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.UserRepository
import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.jdbc.core.simple.JdbcClient

/** The database being unreachable is a 503 with a fixed detail, never a 500 or internals. */
@Import(ServiceUnavailableIT.UnreachableDatabase::class)
class ServiceUnavailableIT : AbstractIntegrationTest() {
    @TestConfiguration
    class UnreachableDatabase {
        @Bean
        @Primary
        fun unreachableUsers(jdbc: JdbcClient): UserRepository = UnreachableUserRepository(jdbc)
    }

    /** Open, because Spring proxies @Repository beans with CGLIB for exception translation. */
    open class UnreachableUserRepository(
        jdbc: JdbcClient,
    ) : UserRepository(jdbc) {
        override fun upsert(identity: VerifiedIdentity): UpsertResult =
            throw CannotGetJdbcConnectionException("Connection to postgres-rw:5432 refused")
    }

    @Test
    fun `a dead database is a service-unavailable problem that names nothing internal`() {
        client
            .put()
            .uri("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isEqualTo(503)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("urn:problem:hl-budget:service-unavailable")
            .jsonPath("$.detail")
            .isEqualTo("The service is temporarily unavailable")
            .jsonPath("$.traceId")
            .exists()
            .consumeWith { result ->
                assertThat(String(result.responseBody ?: ByteArray(0)))
                    .doesNotContain("postgres-rw")
                    .doesNotContain("5432")
                    .doesNotContain("Jdbc")
            }
    }
}
