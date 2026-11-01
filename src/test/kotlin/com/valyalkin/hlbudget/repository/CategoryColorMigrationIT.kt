package com.valyalkin.hlbudget.repository

import com.valyalkin.hlbudget.model.CategoryPalette
import com.valyalkin.hlbudget.model.DefaultCategories
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
 * Feature 007, research R-005: V5 back-fills colours for categories that existed before it. Runs
 * V1-V4 in a schema of its own, adds data the way the old build would have, then applies V5.
 */
class CategoryColorMigrationIT {
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
    fun migrateToV4() {
        flyway(null).clean()
        flyway("4").migrate()
    }

    @AfterEach
    fun dropSchema() {
        flyway(null).clean()
    }

    private fun user(subject: String): UUID =
        jdbc.queryForObject("INSERT INTO users (auth0_subject) VALUES (?) RETURNING id", UUID::class.java, subject)
            ?: error("INSERT returned no id")

    private fun seedDefaults(userId: UUID) {
        DefaultCategories.NAMES.forEach {
            jdbc.update("INSERT INTO categories (user_id, name, is_default) VALUES (?, ?, true)", userId, it)
        }
    }

    private fun custom(
        userId: UUID,
        name: String,
        minute: Int,
    ) {
        jdbc.update(
            "INSERT INTO categories (user_id, name, created_at) " +
                "VALUES (?, ?, TIMESTAMPTZ '2026-10-01 00:00:00Z' + make_interval(mins => ?))",
            userId,
            name,
            minute,
        )
    }

    private fun colors(userId: UUID): Map<String, String> =
        jdbc
            .queryForList("SELECT name, color FROM categories WHERE user_id = ?", userId)
            .associate { it["name"] as String to it["color"] as String }

    @Test
    fun `defaults get their colours by name and custom categories follow creation order`() {
        val alice = user("auth0|alice")
        val bob = user("auth0|bob")
        seedDefaults(alice)
        seedDefaults(bob)
        // Created out of name order, to prove the order is by creation time.
        val customs = listOf("Zoo", "Coffee", "Books", "Yoga", "Gifts", "Bakery", "Music", "Art", "Kids", "Dentist")
        customs.forEachIndexed { minute, name -> custom(alice, name, minute) }

        flyway(null).migrate()

        val expectedDefaults = DefaultCategories.ALL.associate { it.name to it.color }
        assertThat(colors(bob)).isEqualTo(expectedDefaults)
        val aliceColors = colors(alice)
        assertThat(aliceColors.filterKeys { it in expectedDefaults }).isEqualTo(expectedDefaults)
        val spare = CategoryPalette.SPARE
        assertThat(customs.map { aliceColors.getValue(it) })
            .containsExactlyElementsOf(spare + spare.take(3))
    }

    @Test
    fun `past the spare colours twice over, the default colours are reused`() {
        val alice = user("auth0|alice")
        seedDefaults(alice)
        (1..15).forEach { custom(alice, "Custom $it", it) }

        flyway(null).migrate()

        val spare = CategoryPalette.SPARE
        val expected = spare + spare + CategoryPalette.COLORS.first()
        assertThat((1..15).map { colors(alice).getValue("Custom $it") }).containsExactlyElementsOf(expected)
    }

    @Test
    fun `after V5 a colour is required, lower-case hex, with a neutral default for older builds`() {
        flyway(null).migrate()
        val alice = user("auth0|alice")

        jdbc.update("INSERT INTO categories (user_id, name) VALUES (?, 'Old build')", alice)
        assertThat(colors(alice)).containsEntry("Old build", "#8a908b")
        assertThatThrownBy {
            jdbc.update("INSERT INTO categories (user_id, name, color) VALUES (?, 'Bad', '#ZZZZZZ')", alice)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy {
            jdbc.update("INSERT INTO categories (user_id, name, color) VALUES (?, 'Upper', '#ABCDEF')", alice)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy {
            jdbc.update("INSERT INTO categories (user_id, name, color) VALUES (?, 'Null', NULL)", alice)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    companion object {
        private const val SCHEMA = "migration_v5_check"
    }
}
