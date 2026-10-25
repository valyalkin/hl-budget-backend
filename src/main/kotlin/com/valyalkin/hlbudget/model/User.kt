package com.valyalkin.hlbudget.model

import java.time.Instant
import java.util.UUID

/** A person who has signed in at least once. [subject] is the Auth0 `sub`, never exposed by the API. */
data class User(
    val id: UUID,
    val subject: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

/** The outcome of "make my record match my verified identity": the record, and whether it was new. */
data class UpsertResult(
    val user: User,
    val created: Boolean,
)
