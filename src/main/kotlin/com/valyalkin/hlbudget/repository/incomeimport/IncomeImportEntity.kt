package com.valyalkin.hlbudget.repository.incomeimport

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
 * The receipt of one income import (feature 009), the twin of SpendingImportEntity. Written once by
 * [IncomeImportInserts] and never changed, so JPA only reads it.
 */
@Entity
@Immutable
@Table(name = "income_imports")
class IncomeImportEntity(
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
    @Column(name = "income_count", nullable = false, updatable = false)
    var incomeCount: Int,
    @Column(name = "first_date", nullable = false, updatable = false)
    var firstDate: LocalDate,
    @Column(name = "last_date", nullable = false, updatable = false)
    var lastDate: LocalDate,
    @Column(name = "total", nullable = false, updatable = false, precision = 14, scale = 2)
    var total: BigDecimal,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
)
