package com.valyalkin.hlbudget.repository.spending

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One spending. The account and category are plain id columns, not associations: names are
 * resolved per page from the caller's own small lists (research R-010), and the composite foreign
 * keys in the database keep the ids pointing at the same user's rows.
 */
@Entity
@Table(name = "spendings")
class SpendingEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,
    @Column(name = "spending_date", nullable = false)
    var date: LocalDate,
    @Column(name = "merchant", nullable = false, length = 255)
    var merchant: String,
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    var amount: BigDecimal,
    @Column(name = "category_id", nullable = false)
    var categoryId: UUID,
    @Column(name = "account_id", nullable = false)
    var accountId: UUID,
    @Column(name = "idempotency_key", length = 255, updatable = false)
    var idempotencyKey: String? = null,
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "idempotency_fingerprint", length = 64, updatable = false)
    var idempotencyFingerprint: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    // Null until persisted, so Spring Data sees a new entity and inserts instead of merging.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,
)
