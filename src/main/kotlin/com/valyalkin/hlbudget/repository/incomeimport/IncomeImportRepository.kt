package com.valyalkin.hlbudget.repository.incomeimport

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Income imports are only ever read by their owner. */
interface IncomeImportRepository : JpaRepository<IncomeImportEntity, UUID> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): IncomeImportEntity?

    fun findByUserIdAndIdempotencyKey(
        userId: UUID,
        idempotencyKey: String,
    ): IncomeImportEntity?
}
