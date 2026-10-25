package com.valyalkin.hlbudget.repository

import com.valyalkin.hlbudget.model.User
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

fun UserEntity.toModel(): User = User(id, subject, displayName, email, avatarUrl, firstSeenAt, lastSeenAt)

/** A `users` row as returned by the upsert's RETURNING clause. */
internal fun ResultSet.toUser(): User =
    User(
        id = getObject("id", UUID::class.java),
        subject = getString("auth0_subject"),
        displayName = getString("display_name"),
        email = getString("email"),
        avatarUrl = getString("avatar_url"),
        firstSeenAt = getObject("first_seen_at", OffsetDateTime::class.java).toInstant(),
        lastSeenAt = getObject("last_seen_at", OffsetDateTime::class.java).toInstant(),
    )
