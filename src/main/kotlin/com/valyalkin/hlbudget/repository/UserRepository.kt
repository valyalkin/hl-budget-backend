package com.valyalkin.hlbudget.repository

import com.valyalkin.hlbudget.model.UpsertResult
import com.valyalkin.hlbudget.model.VerifiedIdentity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * One atomic statement creates or refreshes the caller's row (research R-004). Two concurrent
 * first calls cannot both insert: the unique constraint turns the loser into an update, and
 * `xmax = 0` tells which call inserted. No find-then-save, no retry, no 409.
 */
@Repository
class UserRepository(
    private val jdbc: JdbcClient,
) {
    fun upsert(identity: VerifiedIdentity): UpsertResult =
        jdbc
            .sql(UPSERT)
            .param("subject", identity.subject)
            .param("displayName", identity.displayName)
            .param("email", identity.email)
            .param("avatarUrl", identity.avatarUrl)
            .query { rs, _ -> UpsertResult(rs.toUser(), rs.getBoolean("inserted")) }
            .single()

    private companion object {
        val UPSERT =
            """
            INSERT INTO users (auth0_subject, display_name, email, avatar_url)
            VALUES (:subject, :displayName, :email, :avatarUrl)
            ON CONFLICT (auth0_subject) DO UPDATE SET
              display_name = EXCLUDED.display_name,
              email = EXCLUDED.email,
              avatar_url = EXCLUDED.avatar_url,
              last_seen_at = now(),
              version = users.version + 1
            RETURNING *, (xmax = 0) AS inserted
            """.trimIndent()
    }
}
