package com.valyalkin.hlbudget.repository

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

/**
 * The context starting at all proves `ddl-auto: validate` accepts Flyway V1-V3 against the
 * entities. The rest pins the constraints of both data models to the database itself.
 */
class SchemaValidationIT : AbstractIntegrationTest() {
    @Test
    fun `V1 to V3 applied cleanly`() {
        val applied =
            jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank",
            )

        assertThat(applied.map { it["version"] }).containsExactly("1", "2", "3")
        assertThat(applied).allSatisfy({ assertThat(it["success"]).isEqualTo(true) })
    }

    @Test
    fun `users has exactly the data-model columns, types and nullability`() {
        val columns =
            jdbcTemplate
                .queryForList(
                    "SELECT column_name, data_type, is_nullable, character_maximum_length " +
                        "FROM information_schema.columns WHERE table_name = 'users'",
                ).associateBy { it["column_name"] as String }

        assertThat(columns.keys).containsExactlyInAnyOrder(
            "id",
            "auth0_subject",
            "display_name",
            "email",
            "avatar_url",
            "first_seen_at",
            "last_seen_at",
            "version",
            "categories_seeded_at",
        )
        assertThat(columns.getValue("categories_seeded_at")["is_nullable"]).isEqualTo("YES")
        assertThat(columns.getValue("id")["data_type"]).isEqualTo("uuid")
        assertThat(columns.getValue("auth0_subject")["character_maximum_length"]).isEqualTo(255)
        assertThat(columns.getValue("auth0_subject")["is_nullable"]).isEqualTo("NO")
        assertThat(columns.getValue("email")["character_maximum_length"]).isEqualTo(320)
        assertThat(columns.getValue("email")["is_nullable"]).isEqualTo("YES")
        assertThat(columns.getValue("avatar_url")["character_maximum_length"]).isEqualTo(2048)
        assertThat(columns.getValue("first_seen_at")["data_type"]).isEqualTo("timestamp with time zone")
        assertThat(columns.getValue("version")["data_type"]).isEqualTo("bigint")
    }

    @Test
    fun `the named constraints exist and there is no email index`() {
        val constraints =
            jdbcTemplate
                .queryForList(
                    "SELECT conname FROM pg_constraint WHERE conrelid = 'users'::regclass",
                    String::class.java,
                )
        val indexes =
            jdbcTemplate.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = 'users'", String::class.java)

        assertThat(constraints).contains("pk_users", "uq_users_auth0_subject", "ck_users_auth0_subject_not_blank")
        assertThat(indexes).noneMatch { it?.contains("(email)") == true }
    }

    @Test
    fun `a blank subject is refused by the database`() {
        assertThatThrownBy { jdbcTemplate.update("INSERT INTO users (auth0_subject) VALUES ('   ')") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    // ------------------------------------------------------------------------------- feature 002

    private fun columnsOf(table: String): Map<String, Map<String, Any?>> =
        jdbcTemplate
            .queryForList(
                "SELECT column_name, data_type, is_nullable, character_maximum_length, numeric_precision, " +
                    "numeric_scale FROM information_schema.columns WHERE table_name = ?",
                table,
            ).associateBy { it["column_name"] as String }

    private fun constraintsOf(table: String): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT conname FROM pg_constraint WHERE conrelid = ?::regclass",
            String::class.java,
            table,
        )

    private fun indexesOf(table: String): List<String?> =
        jdbcTemplate.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = ?", String::class.java, table)

    private fun user(subject: String): UUID =
        jdbcTemplate.queryForObject(
            "INSERT INTO users (auth0_subject) VALUES (?) RETURNING id",
            UUID::class.java,
            subject,
        ) ?: error("INSERT returned no id")

    private fun account(
        userId: UUID,
        name: String,
    ): UUID =
        jdbcTemplate.queryForObject(
            "INSERT INTO accounts (user_id, name) VALUES (?, ?) RETURNING id",
            UUID::class.java,
            userId,
            name,
        ) ?: error("INSERT returned no id")

    private fun category(
        userId: UUID,
        name: String,
    ): UUID =
        jdbcTemplate.queryForObject(
            "INSERT INTO categories (user_id, name) VALUES (?, ?) RETURNING id",
            UUID::class.java,
            userId,
            name,
        ) ?: error("INSERT returned no id")

    @Suppress("LongParameterList")
    private fun spending(
        userId: UUID,
        categoryId: UUID,
        accountId: UUID,
        amount: String = "12.50",
        key: String? = null,
        fingerprint: String? = null,
    ): Int =
        jdbcTemplate.update(
            "INSERT INTO spendings (user_id, spending_date, merchant, amount, category_id, account_id, " +
                "idempotency_key, idempotency_fingerprint) " +
                "VALUES (?, DATE '2026-10-20', 'Loblaws', ?::numeric, ?, ?, ?, ?)",
            userId,
            amount,
            categoryId,
            accountId,
            key,
            fingerprint,
        )

    @Test
    fun `accounts categories and spendings have the data-model columns`() {
        assertThat(columnsOf("accounts").keys)
            .containsExactlyInAnyOrder("id", "user_id", "name", "created_at", "updated_at", "version")
        assertThat(columnsOf("categories").keys)
            .containsExactlyInAnyOrder("id", "user_id", "name", "is_default", "created_at", "updated_at", "version")
        val spendings = columnsOf("spendings")
        assertThat(spendings.keys).containsExactlyInAnyOrder(
            "id",
            "user_id",
            "spending_date",
            "merchant",
            "amount",
            "category_id",
            "account_id",
            "idempotency_key",
            "idempotency_fingerprint",
            "created_at",
            "updated_at",
            "version",
        )
        assertThat(columnsOf("accounts").getValue("name")["character_maximum_length"]).isEqualTo(50)
        assertThat(spendings.getValue("amount")["numeric_precision"]).isEqualTo(10)
        assertThat(spendings.getValue("amount")["numeric_scale"]).isEqualTo(2)
        assertThat(spendings.getValue("spending_date")["data_type"]).isEqualTo("date")
        assertThat(spendings.getValue("merchant")["character_maximum_length"]).isEqualTo(255)
        assertThat(spendings.getValue("idempotency_key")["is_nullable"]).isEqualTo("YES")
        assertThat(spendings.getValue("idempotency_fingerprint")["character_maximum_length"]).isEqualTo(64)
    }

    @Test
    fun `the named constraints and indexes exist`() {
        assertThat(constraintsOf("accounts"))
            .contains("pk_accounts", "uq_accounts_id_user", "fk_accounts_user", "ck_accounts_name")
        assertThat(constraintsOf("categories"))
            .contains("pk_categories", "uq_categories_id_user", "fk_categories_user", "ck_categories_name")
        assertThat(constraintsOf("spendings")).contains(
            "pk_spendings",
            "fk_spendings_user",
            "fk_spendings_category",
            "fk_spendings_account",
            "ck_spendings_amount",
            "ck_spendings_merchant",
            "ck_spendings_idempotency_pair",
        )
        assertThat(indexesOf("accounts")).contains("ux_accounts_user_lower_name")
        assertThat(indexesOf("categories")).contains("ux_categories_user_lower_name")
        assertThat(indexesOf("spendings")).contains(
            "ux_spendings_user_idempotency_key",
            "ix_spendings_user_date",
            "ix_spendings_user_category",
            "ix_spendings_user_account",
        )
    }

    @Test
    fun `names must be trimmed, 1 to 50 characters and unique per user ignoring case`() {
        val alice = user("auth0|alice")
        val bob = user("auth0|bob")
        account(alice, "Amex")

        assertThatThrownBy { account(alice, " x") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { account(alice, "a".repeat(51)) }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { account(alice, "AMEX") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(account(bob, "Amex")).isNotNull()

        category(alice, "Coffee")
        assertThatThrownBy { category(alice, "coffee") }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { category(alice, "") }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a spending must be positive and reference its own user's account and category`() {
        val alice = user("auth0|alice")
        val bob = user("auth0|bob")
        val aliceCategory = category(alice, "Groceries")
        val aliceAccount = account(alice, "Amex")
        val bobAccount = account(bob, "Cash")

        assertThat(spending(alice, aliceCategory, aliceAccount)).isEqualTo(1)
        assertThatThrownBy { spending(alice, aliceCategory, aliceAccount, amount = "0") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { spending(alice, aliceCategory, bobAccount) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `a referenced account or category cannot be deleted`() {
        val alice = user("auth0|alice")
        val categoryId = category(alice, "Groceries")
        val accountId = account(alice, "Amex")
        spending(alice, categoryId, accountId)

        assertThatThrownBy { jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { jdbcTemplate.update("DELETE FROM categories WHERE id = ?", categoryId) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `deleting a user cascades to everything they own`() {
        val alice = user("auth0|alice")
        spending(alice, category(alice, "Groceries"), account(alice, "Amex"))

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", alice)

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM spendings", Int::class.java)).isZero()
    }

    @Test
    fun `an idempotency key needs a fingerprint and is unique per user`() {
        val alice = user("auth0|alice")
        val bob = user("auth0|bob")
        val categoryId = category(alice, "Groceries")
        val accountId = account(alice, "Amex")
        val fingerprint = "a".repeat(64)

        assertThatThrownBy { spending(alice, categoryId, accountId, key = "k1") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        spending(alice, categoryId, accountId, key = "k1", fingerprint = fingerprint)
        assertThatThrownBy { spending(alice, categoryId, accountId, key = "k1", fingerprint = fingerprint) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        spending(alice, categoryId, accountId)
        spending(alice, categoryId, accountId)
        spending(bob, category(bob, "Taxi"), account(bob, "Cash"), key = "k1", fingerprint = fingerprint)
    }
}
