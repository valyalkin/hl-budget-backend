package com.valyalkin.hlbudget.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/** The caller's `users` row as the budgeting features need it: its id and whether defaults exist. */
data class CurrentUserRow(
    val id: UUID,
    val categoriesSeededAt: Instant?,
)

/**
 * Turns a verified Auth0 subject into a `users.id` (research R-002) and claims the one-time seeding
 * of the default categories (research R-003). Profile fields are never touched here; that stays the
 * job of `PUT /api/v1/users/me`.
 */
@Repository
class CurrentUserRepository(
    private val jdbc: JdbcClient,
) {
    fun findBySubject(subject: String): CurrentUserRow? =
        jdbc
            .sql("SELECT id, categories_seeded_at FROM users WHERE auth0_subject = :subject")
            .param("subject", subject)
            .query { rs, _ ->
                CurrentUserRow(
                    id = rs.getObject("id", UUID::class.java),
                    categoriesSeededAt = rs.getObject("categories_seeded_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }.optional()
            .orElse(null)

    /** The unique constraint decides between concurrent first calls; the loser inserts nothing. */
    fun insertIfMissing(subject: String) {
        jdbc
            .sql("INSERT INTO users (auth0_subject) VALUES (:subject) ON CONFLICT (auth0_subject) DO NOTHING")
            .param("subject", subject)
            .update()
    }

    /**
     * True for exactly one caller per user. A concurrent second transaction blocks on the row lock
     * until the first commits, then matches nothing.
     */
    fun claimSeeding(userId: UUID): Boolean =
        jdbc
            .sql(
                "UPDATE users SET categories_seeded_at = now() " +
                    "WHERE id = :id AND categories_seeded_at IS NULL RETURNING id",
            ).param("id", userId)
            .query(UUID::class.java)
            .optional()
            .isPresent

    /** Runs once per user, right after a successful [claimSeeding], in the same transaction. */
    fun insertDefaultCategories(
        userId: UUID,
        names: List<String>,
    ) {
        names.forEach { name ->
            jdbc
                .sql("INSERT INTO categories (user_id, name, is_default) VALUES (:userId, :name, true)")
                .param("userId", userId)
                .param("name", name)
                .update()
        }
    }
}
