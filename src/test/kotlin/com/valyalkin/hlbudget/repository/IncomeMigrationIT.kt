package com.valyalkin.hlbudget.repository

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
 * Feature 009, research R-001: V7 adds the income tables without touching existing data, and its
 * constraints keep an income on its own user's income category and account. Runs V1-V6 in a schema
 * of its own, adds data the way the old build would have, then applies V7.
 */
class IncomeMigrationIT {
    private val postgres = AbstractIntegrationTest.postgres
    private val dataSource =
        DriverManagerDataSource(
            postgres.jdbcUrl + (if ("?" in postgres.jdbcUrl) "&" else "?") + "currentSchema=$SCHEMA",
            postgres.username,
            postgres.password,
        )
    private val jdbc = JdbcTemplate(dataSource)
    private lateinit var userId: UUID
    private lateinit var categoryId: UUID
    private lateinit var accountId: UUID

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
    fun migrateToV6ThenLatest() {
        flyway(null).clean()
        flyway("6").migrate()
        userId =
            id("INSERT INTO users (auth0_subject, categories_seeded_at) VALUES ('auth0|alice', now()) RETURNING id")
        categoryId =
            id("INSERT INTO categories (user_id, name, color) VALUES ('$userId', 'Groceries', '#c2410c') RETURNING id")
        accountId =
            id("INSERT INTO accounts (user_id, name, color) VALUES ('$userId', 'Amex', '#e11d48') RETURNING id")
        jdbc.update(
            "INSERT INTO spendings (user_id, spending_date, merchant, amount, category_id, account_id) " +
                "VALUES (?, DATE '2026-10-01', 'Loblaws', 12.50, ?, ?)",
            userId,
            categoryId,
            accountId,
        )
        flyway(null).migrate()
    }

    @AfterEach
    fun dropSchema() {
        flyway(null).clean()
    }

    private fun id(sql: String): UUID = jdbc.queryForObject(sql, UUID::class.java) ?: error("no id from $sql")

    private fun incomeCategory(
        name: String,
        color: String = "#1e5b45",
    ): UUID =
        id(
            "INSERT INTO income_categories (user_id, name, color) VALUES ('$userId', '$name', '$color') RETURNING id",
        )

    private fun income(
        category: UUID,
        account: UUID = accountId,
        source: String = "Acme Corp",
        amount: String = "5000.00",
        key: String? = null,
        fingerprint: String? = null,
    ) = jdbc.update(
        "INSERT INTO incomes (user_id, income_date, source, amount, category_id, account_id, " +
            "idempotency_key, idempotency_fingerprint) VALUES (?, DATE '2026-10-15', ?, ?::numeric, ?, ?, ?, ?)",
        userId,
        source,
        amount,
        category,
        account,
        key,
        fingerprint,
    )

    @Test
    fun `existing data is untouched and every user still needs income seeding`() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spendings", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT name FROM categories", String::class.java)).isEqualTo("Groceries")
        assertThat(jdbc.queryForObject("SELECT color FROM accounts", String::class.java)).isEqualTo("#e11d48")
        assertThat(jdbc.queryForObject("SELECT income_categories_seeded_at FROM users", Any::class.java)).isNull()
        assertThat(jdbc.queryForObject("SELECT categories_seeded_at FROM users", Any::class.java)).isNotNull()
        listOf("income_categories", "incomes", "income_imports").forEach { table ->
            assertThat(jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)).isZero()
        }
    }

    @Test
    fun `an income can use an income category and an account`() {
        income(incomeCategory("Salary"))

        assertThat(jdbc.queryForObject("SELECT count(*) FROM incomes", Int::class.java)).isEqualTo(1)
    }

    @Test
    fun `an income cannot use a spending category`() {
        assertThatThrownBy { income(categoryId) }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `the amount, source and idempotency pair are checked`() {
        val salary = incomeCategory("Salary")
        assertThatThrownBy { income(salary, amount = "0") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { income(salary, source = " x") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { income(salary, key = "k1") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `income category names are unique per user ignoring case, separately from spending categories`() {
        incomeCategory("Salary")
        assertThatThrownBy { incomeCategory("salary") }.isInstanceOf(DataIntegrityViolationException::class.java)

        incomeCategory("Groceries")

        assertThat(jdbc.queryForObject("SELECT count(*) FROM income_categories", Int::class.java)).isEqualTo(2)
    }

    @Test
    fun `an income category colour must be a lower-case hex colour`() {
        assertThatThrownBy { incomeCategory("Gifts", color = "red") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `an account used by an income cannot be deleted`() {
        jdbc.update("DELETE FROM spendings")
        income(incomeCategory("Salary"))

        assertThatThrownBy { jdbc.update("DELETE FROM accounts WHERE id = ?", accountId) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private companion object {
        const val SCHEMA = "migration_v7_check"
    }
}
