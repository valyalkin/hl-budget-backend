package com.valyalkin.hlbudget.repository.spendingimport

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The receipt of one import (feature 004, research R-006). Written once by
 * [SpendingImportInserts] and never changed, so JPA only reads it.
 */
@Entity
@Immutable
@Table(name = "spending_imports")
class SpendingImportEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,
    @Column(name = "idempotency_key", length = 255, updatable = false)
    var idempotencyKey: String? = null,
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "idempotency_fingerprint", length = 64, updatable = false)
    var idempotencyFingerprint: String? = null,
    @Column(name = "spending_count", nullable = false, updatable = false)
    var spendingCount: Int,
    @Column(name = "first_date", nullable = false, updatable = false)
    var firstDate: LocalDate,
    @Column(name = "last_date", nullable = false, updatable = false)
    var lastDate: LocalDate,
    @Column(name = "total", nullable = false, updatable = false, precision = 14, scale = 2)
    var total: BigDecimal,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
