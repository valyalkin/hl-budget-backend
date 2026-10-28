package com.valyalkin.hlbudget.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** An account or category as a spending needs it: its id and current name. */
data class NamedItemRow(
    val id: UUID,
    val name: String,
)

/**
 * Find-or-create of an account or category by name, for a spending saved with a typed name
 * (feature 003, research R-004).
 *
 * A plain INSERT that loses a race on the case-insensitive unique index would fail the statement
 * and abort the transaction, so the spending could not be saved. `ON CONFLICT … DO NOTHING` waits
 * for the other transaction instead; the SELECT that follows then sees whichever row won.
 */
@Repository
class NamedItemInserts(
    private val jdbc: JdbcClient,
) {
    /** The caller's category with this name ignoring case, created (not a default) when missing. */
    fun findOrCreateCategory(
        userId: UUID,
        name: String,
        now: Instant,
    ): NamedItemRow =
        findOrCreate(
            insert =
                "INSERT INTO categories (user_id, name, is_default, created_at, updated_at) " +
                    "VALUES (:userId, :name, false, :now, :now) ON CONFLICT (user_id, lower(name)) DO NOTHING",
            select = "SELECT id, name FROM categories WHERE user_id = :userId AND lower(name) = lower(:name)",
            userId = userId,
            name = name,
            now = now,
        )

    /** The caller's account with this name ignoring case, created when missing. */
    fun findOrCreateAccount(
        userId: UUID,
        name: String,
        now: Instant,
    ): NamedItemRow =
        findOrCreate(
            insert =
                "INSERT INTO accounts (user_id, name, created_at, updated_at) " +
                    "VALUES (:userId, :name, :now, :now) ON CONFLICT (user_id, lower(name)) DO NOTHING",
            select = "SELECT id, name FROM accounts WHERE user_id = :userId AND lower(name) = lower(:name)",
            userId = userId,
            name = name,
            now = now,
        )

    private fun findOrCreate(
        insert: String,
        select: String,
        userId: UUID,
        name: String,
        now: Instant,
    ): NamedItemRow {
        jdbc
            .sql(insert)
            .param("userId", userId)
            .param("name", name)
            .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
            .update()
        return jdbc
            .sql(select)
            .param("userId", userId)
            .param("name", name)
            .query { rs, _ -> NamedItemRow(rs.getObject("id", UUID::class.java), rs.getString("name")) }
            .single()
    }
}
