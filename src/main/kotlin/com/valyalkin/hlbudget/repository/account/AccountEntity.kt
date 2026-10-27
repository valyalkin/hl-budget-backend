package com.valyalkin.hlbudget.repository.account

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** Where the money was paid from. One row per account; the name is the only user-editable field. */
@Entity
@Table(name = "accounts")
class AccountEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,
    @Column(name = "name", nullable = false, length = 50)
    var name: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    // Null until persisted, so Spring Data sees a new entity and inserts instead of merging.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,
)
