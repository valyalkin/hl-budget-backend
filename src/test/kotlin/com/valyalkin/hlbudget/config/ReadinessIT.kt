package com.valyalkin.hlbudget.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.time.Instant

/**
 * SC-007 with its own Postgres, so the shared one is never disturbed. The container is paused
 * rather than stopped: a restarted container gets a new port, a paused one looks to the service
 * exactly like a database that stopped answering.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class ReadinessIT {
    @Autowired
    private lateinit var client: RestTestClient

    private fun status(path: String): Int =
        client
            .get()
            .uri(path)
            .exchange()
            .returnResult(String::class.java)
            .status
            .value()

    private fun awaitStatus(
        path: String,
        expected: Int,
    ): Duration {
        val started = Instant.now()
        while (Duration.between(started, Instant.now()) < LIMIT) {
            if (status(path) == expected) return Duration.between(started, Instant.now())
            Thread.sleep(POLL_MILLIS)
        }
        error("$path did not answer $expected within $LIMIT")
    }

    @Test
    fun `readyz follows the database while healthz stays up`() {
        assertThat(status("/readyz")).isEqualTo(200)

        pause(true)
        try {
            assertThat(awaitStatus("/readyz", 503)).isLessThan(LIMIT)
            assertThat(status("/healthz")).isEqualTo(200)
        } finally {
            pause(false)
        }

        assertThat(awaitStatus("/readyz", 200)).isLessThan(LIMIT)
    }

    private fun pause(paused: Boolean) {
        val docker = postgres.dockerClient
        if (paused) {
            docker.pauseContainerCmd(postgres.containerId).exec()
        } else {
            docker.unpauseContainerCmd(postgres.containerId).exec()
        }
    }

    companion object {
        private val LIMIT: Duration = Duration.ofSeconds(30)
        private const val POLL_MILLIS = 500L

        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:17.7-alpine")
                .withDatabaseName("hl-budget")
                .withUsername("hl-budget")
                .withPassword("test")
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            // Never called: readiness does not depend on Auth0. Values only satisfy validation.
            registry.add("hlbudget.auth.jwk-set-uri") { "http://localhost:1/.well-known/jwks.json" }
            registry.add("hlbudget.auth.issuer") { "https://unused.example/" }
            registry.add("hlbudget.auth.audience") { "https://unused.example" }
            registry.add("hlbudget.auth.claims-namespace") { "https://hl-budget/" }
        }

        @JvmStatic
        @AfterAll
        fun stopContainer() = postgres.stop()
    }
}
