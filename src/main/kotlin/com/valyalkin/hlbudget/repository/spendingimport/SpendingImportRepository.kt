package com.valyalkin.hlbudget.repository.spendingimport

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Imports are only ever read by their owner. */
interface SpendingImportRepository : JpaRepository<SpendingImportEntity, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): SpendingImportEntity?

    fun findByUserIdAndIdempotencyKey(
        userId: UUID,
        idempotencyKey: String,
    ): SpendingImportEntity?
}
