package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.time.Duration
import java.time.LocalDate

/**
 * Feature 004 (research R-014): a full 1,000-row import commits in well under 2 seconds, and each
 * new category or account name is created once however many rows use it.
 */
class SpendingImportPerformanceIT : AbstractIntegrationTest() {
    private fun body(run: Int): String {
        val start = LocalDate.parse("2026-07-01")
        val rows =
            (0 until 1000).joinToString(",") { i ->
                // 20 category names (10 defaults, 10 new) and 3 account names, in varying case.
                val category = if (i % 20 < 10) DEFAULTS[i % 10] else "Import Category ${i % 20}"
                val account = listOf("Visa", "AMEX", "cash")[i % 3]
                """{"date":"${start.plusDays((i % 118).toLong())}","merchant":"Run $run Shop ${i % 50}",""" +
                    """"amount":{"amount":"${1 + i % 300}.${10 + i % 90}","currency":"CAD"},""" +
                    """"categoryName":"$category","accountName":"$account"}"""
            }
        return """{"spendings":[$rows]}"""
    }

    private fun timedImport(run: Int): Duration {
        val began = System.nanoTime()
        client
            .post()
            .uri("/api/v1/spending-imports")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $alice")
            .header("Idempotency-Key", "perf-run-$run")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body(run))
            .exchange()
            .expectStatus()
            .isCreated()
        return Duration.ofNanos(System.nanoTime() - began)
    }

    @Test
    fun `1,000 rows import in under 2 seconds, with each new name created once`() {
        categoryIds(alice)
        timedImport(0)

        val runs = (1..3).map { timedImport(it) }.sorted()

        assertThat(runs[1]).isLessThan(Duration.ofSeconds(2))
        assertThat(count("SELECT count(*) FROM spendings")).isEqualTo(4000)
        assertThat(count("SELECT count(*) FROM categories WHERE name LIKE 'Import Category %'")).isEqualTo(10)
        assertThat(count("SELECT count(*) FROM accounts")).isEqualTo(3)
        assertThat(count("SELECT count(*) FROM spending_imports")).isEqualTo(4)
    }

    private companion object {
        val DEFAULTS =
            listOf(
                "Groceries",
                "Car",
                "Taxi",
                "Health",
                "Sport",
                "Clothes",
                "Education",
                "Household",
                "Rent",
                "Public Transport",
            )
    }
}
