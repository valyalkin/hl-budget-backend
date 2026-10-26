package com.valyalkin.hlbudget.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * The persistence shape of a user. Writes go through the single upsert in [UserRepository]; the
 * entity keeps Hibernate's `ddl-auto: validate` honest about the schema and serves future reads.
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,
    @Column(name = "auth0_subject", nullable = false, updatable = false, length = 255)
    var subject: String,
    @Column(name = "display_name", length = 255)
    var displayName: String?,
    @Column(name = "email", length = 320)
    var email: String?,
    @Column(name = "avatar_url", length = 2048)
    var avatarUrl: String?,
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    var firstSeenAt: Instant,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
)
