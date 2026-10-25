package com.valyalkin.hlbudget.repository

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException

/** The context starting at all proves `ddl-auto: validate` accepts Flyway V1 against the entity. */
class SchemaValidationIT : AbstractIntegrationTest() {
    @Test
    fun `V1 applied cleanly`() {
        val applied =
            jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank",
            )

        assertThat(applied.map { it["version"] }).containsExactly("1")
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
        )
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
}
