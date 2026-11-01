package com.valyalkin.hlbudget.repository.incomecategory

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** What kind of income it is (feature 009). [isDefault] rows were created by seeding and never change. */
@Entity
@Table(name = "income_categories")
class IncomeCategoryEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: UUID,
    @Column(name = "name", nullable = false, length = 50)
    var name: String,
    @Column(name = "is_default", nullable = false, updatable = false)
    var isDefault: Boolean = false,
    // Fixed when the category is created (research R-002); a rename keeps it.
    @Column(name = "color", nullable = false, updatable = false, length = 7)
    var color: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    // Null until persisted, so Spring Data sees a new entity and inserts instead of merging.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,
)
