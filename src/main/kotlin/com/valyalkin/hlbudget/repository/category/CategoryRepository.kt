package com.valyalkin.hlbudget.repository.category

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** A category with the number of the owner's spendings that use it, read in one query. */
data class CategoryRow(
    val id: UUID,
    val name: String,
    val isDefault: Boolean,
    val spendingCount: Long,
)

interface CategoryRepository : JpaRepository<CategoryEntity, UUID> {
    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.category.CategoryRow(c.id, c.name, c.isDefault, " +
            "(SELECT count(s) FROM SpendingEntity s WHERE s.userId = c.userId AND s.categoryId = c.id)) " +
            "FROM CategoryEntity c WHERE c.userId = :userId ORDER BY lower(c.name), c.id",
    )
    fun findAllWithCounts(userId: UUID): List<CategoryRow>

    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.category.CategoryRow(c.id, c.name, c.isDefault, " +
            "(SELECT count(s) FROM SpendingEntity s WHERE s.userId = c.userId AND s.categoryId = c.id)) " +
            "FROM CategoryEntity c WHERE c.userId = :userId AND c.id = :id",
    )
    fun findWithCount(
        userId: UUID,
        id: UUID,
    ): CategoryRow?

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): CategoryEntity?

    fun findAllByUserId(userId: UUID): List<CategoryEntity>
}
