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
    val color: String,
)

/** How many of a user's categories, or of their accounts, use one colour. */
interface ColorCount {
    val color: String
    val count: Long
}

interface CategoryRepository : JpaRepository<CategoryEntity, UUID> {
    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.category.CategoryRow(c.id, c.name, c.isDefault, " +
            "(SELECT count(s) FROM SpendingEntity s WHERE s.userId = c.userId AND s.categoryId = c.id), c.color) " +
            "FROM CategoryEntity c WHERE c.userId = :userId ORDER BY lower(c.name), c.id",
    )
    fun findAllWithCounts(userId: UUID): List<CategoryRow>

    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.category.CategoryRow(c.id, c.name, c.isDefault, " +
            "(SELECT count(s) FROM SpendingEntity s WHERE s.userId = c.userId AND s.categoryId = c.id), c.color) " +
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

    /** Feature 007, research R-008: a name given with a spending must match an existing item. */
    fun findByUserIdAndNameIgnoreCase(
        userId: UUID,
        name: String,
    ): CategoryEntity?

    /** Feature 007, research R-004: what a new category's colour is chosen from. */
    @Query(
        "SELECT c.color AS color, count(c) AS count FROM CategoryEntity c WHERE c.userId = :userId GROUP BY c.color",
    )
    fun colorCounts(userId: UUID): List<ColorCount>

    fun existsByUserIdAndNameIgnoreCase(
        userId: UUID,
        name: String,
    ): Boolean

    fun existsByUserIdAndNameIgnoreCaseAndIdNot(
        userId: UUID,
        name: String,
        id: UUID,
    ): Boolean

    /** `FOR UPDATE`, for the same reason as in AccountRepository: the in-use count read next is final. */
    @Query(value = "SELECT * FROM categories WHERE id = :id AND user_id = :userId FOR UPDATE", nativeQuery = true)
    fun lockByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): CategoryEntity?
}
