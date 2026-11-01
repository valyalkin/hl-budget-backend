package com.valyalkin.hlbudget.repository.income

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface IncomeRepository :
    JpaRepository<IncomeEntity, UUID>,
    JpaSpecificationExecutor<IncomeEntity> {
    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): IncomeEntity?

    fun findByUserIdAndIdempotencyKey(
        userId: UUID,
        idempotencyKey: String,
    ): IncomeEntity?

    fun countByUserIdAndAccountId(
        userId: UUID,
        accountId: UUID,
    ): Long

    fun countByUserIdAndCategoryId(
        userId: UUID,
        categoryId: UUID,
    ): Long
}
