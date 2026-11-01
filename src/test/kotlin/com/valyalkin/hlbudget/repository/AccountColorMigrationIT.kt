package com.valyalkin.hlbudget.repository

import com.valyalkin.hlbudget.model.CategoryPalette
import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID

/**
 * Feature 008, research R-003: V6 gives accounts that existed before it a colour, in creation order
 * per user, following the same sequence a new account would get. Runs V1-V5 in a schema of its own,
 * adds accounts the way the old build would have, then applies V6.
 */
class AccountColorMigrationIT {
    private val postgres = AbstractIntegrationTest.postgres
    private val dataSource =
        DriverManagerDataSource(
            postgres.jdbcUrl + (if ("?" in postgres.jdbcUrl) "&" else "?") + "currentSchema=$SCHEMA",
            postgres.username,
            postgres.password,
        )
    private val jdbc = JdbcTemplate(dataSource)

    private fun flyway(target: String?): Flyway =
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(SCHEMA)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .apply { if (target != null) target(target) }
            .load()

    @BeforeEach
    fun migrateToV5() {
        flyway(null).clean()
        flyway("5").migrate()
    }

    @AfterEach
    fun dropSchema() {
        flyway(null).clean()
    }

    private fun user(subject: String): UUID =
        jdbc.queryForObject("INSERT INTO users (auth0_subject) VALUES (?) RETURNING id", UUID::class.java, subject)
            ?: error("INSERT returned no id")

    private fun account(
        userId: UUID,
        name: String,
        minute: Int,
    ) {
        jdbc.update(
            "INSERT INTO accounts (user_id, name, created_at) " +
                "VALUES (?, ?, TIMESTAMPTZ '2026-10-01 00:00:00Z' + make_interval(mins => ?))",
            userId,
            name,
            minute,
        )
    }

    private fun colors(userId: UUID): Map<String, String> =
        jdbc
            .queryForList("SELECT name, color FROM accounts WHERE user_id = ?", userId)
            .associate { it["name"] as String to it["color"] as String }

    @Test
    fun `existing accounts get colours in creation order, spare colours first, per user`() {
        val alice = user("auth0|alice")
        val bob = user("auth0|bob")
        // Created out of name order, to prove the order is by creation time.
        val aliceAccounts = listOf("Visa", "Amex", "Cash", "Wise", "Debit", "Savings", "Joint", "Bonds", "Crypto")
        aliceAccounts.forEachIndexed { minute, name -> account(alice, name, minute) }
        account(bob, "Bob card", 1)
        account(bob, "Bob cash", 2)

        flyway(null).migrate()

        val expected = CategoryPalette.SPARE + CategoryPalette.DEFAULTS.take(2)
        assertThat(aliceAccounts.map { colors(alice).getValue(it) }).containsExactlyElementsOf(expected)
        assertThat(listOf("Bob card", "Bob cash").map { colors(bob).getValue(it) })
            .containsExactlyElementsOf(CategoryPalette.SPARE.take(2))
    }

    @Test
    fun `the sequence matches what CategoryPalette next gives one account at a time`() {
        val alice = user("auth0|alice")
        (1..30).forEach { account(alice, "Account $it", it) }

        flyway(null).migrate()

        val counts = mutableMapOf<String, Int>()
        val expected =
            (1..30).map {
                CategoryPalette.next(counts).also { color -> counts.merge(color, 1, Int::plus) }
            }
        assertThat((1..30).map { colors(alice).getValue("Account $it") }).containsExactlyElementsOf(expected)
    }

    @Test
    fun `after V6 a colour is required, lower-case hex, with a neutral default for older builds`() {
        flyway(null).migrate()
        val alice = user("auth0|alice")

        jdbc.update("INSERT INTO accounts (user_id, name) VALUES (?, 'Old build')", alice)
        assertThat(colors(alice)).containsEntry("Old build", "#8a908b")
        assertThatThrownBy {
            jdbc.update("UPDATE accounts SET color = 'red' WHERE user_id = ?", alice)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy {
            jdbc.update("INSERT INTO accounts (user_id, name, color) VALUES (?, 'Upper', '#ABCDEF')", alice)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy {
            jdbc.update("INSERT INTO accounts (user_id, name, color) VALUES (?, 'Null', NULL)", alice)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    companion object {
        private const val SCHEMA = "migration_v6_check"
    }
}
