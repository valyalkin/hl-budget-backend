package com.valyalkin.hlbudget.repository.spending

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpendingRepository : JpaRepository<SpendingEntity, UUID> {
    fun countByUserIdAndAccountId(
        userId: UUID,
        accountId: UUID,
    ): Long

    fun countByUserIdAndCategoryId(
        userId: UUID,
        categoryId: UUID,
    ): Long
}
