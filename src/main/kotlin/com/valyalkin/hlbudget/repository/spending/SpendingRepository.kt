package com.valyalkin.hlbudget.repository.spending

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface SpendingRepository :
    JpaRepository<SpendingEntity, UUID>,
    JpaSpecificationExecutor<SpendingEntity> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): SpendingEntity?

    fun findByUserIdAndIdempotencyKey(
        userId: UUID,
        idempotencyKey: String,
    ): SpendingEntity?

    fun countByUserIdAndAccountId(
        userId: UUID,
        accountId: UUID,
    ): Long

    fun countByUserIdAndCategoryId(
        userId: UUID,
        categoryId: UUID,
    ): Long
}
