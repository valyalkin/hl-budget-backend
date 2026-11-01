package com.valyalkin.hlbudget.repository.incomecategory

import com.valyalkin.hlbudget.repository.category.ColorCount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** An income category with the number of the owner's income entries that use it, read in one query. */
data class IncomeCategoryRow(
    val id: UUID,
    val name: String,
    val isDefault: Boolean,
    val incomeCount: Long,
    val color: String,
)

interface IncomeCategoryRepository : JpaRepository<IncomeCategoryEntity, UUID> {
    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRow(c.id, c.name, c.isDefault, " +
            "(SELECT count(i) FROM IncomeEntity i WHERE i.userId = c.userId AND i.categoryId = c.id), c.color) " +
            "FROM IncomeCategoryEntity c WHERE c.userId = :userId ORDER BY lower(c.name), c.id",
    )
    fun findAllWithCounts(userId: UUID): List<IncomeCategoryRow>

    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRow(c.id, c.name, c.isDefault, " +
            "(SELECT count(i) FROM IncomeEntity i WHERE i.userId = c.userId AND i.categoryId = c.id), c.color) " +
            "FROM IncomeCategoryEntity c WHERE c.userId = :userId AND c.id = :id",
    )
    fun findWithCount(
        userId: UUID,
        id: UUID,
    ): IncomeCategoryRow?

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): IncomeCategoryEntity?

    fun findAllByUserId(userId: UUID): List<IncomeCategoryEntity>

    /** A name given with an income must match an existing income category; nothing is created. */
    fun findByUserIdAndNameIgnoreCase(
        userId: UUID,
        name: String,
    ): IncomeCategoryEntity?

    /** What a new income category's colour is chosen from (research R-002): income categories only. */
    @Query(
        "SELECT c.color AS color, count(c) AS count FROM IncomeCategoryEntity c " +
            "WHERE c.userId = :userId GROUP BY c.color",
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

    /** `FOR UPDATE`, as in CategoryRepository: the in-use count read next is final. */
    @Query(
        value = "SELECT * FROM income_categories WHERE id = :id AND user_id = :userId FOR UPDATE",
        nativeQuery = true,
    )
    fun lockByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): IncomeCategoryEntity?
}
