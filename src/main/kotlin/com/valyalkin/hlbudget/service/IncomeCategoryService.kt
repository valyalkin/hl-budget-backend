package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.model.CategoryPalette
import com.valyalkin.hlbudget.model.IncomeCategory
import com.valyalkin.hlbudget.model.NameCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.income.IncomeRepository
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryEntity
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRepository
import com.valyalkin.hlbudget.repository.incomecategory.toModel
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The caller's income categories (feature 009, US4), the twin of [CategoryService]. Salary and
 * Investments are fixed; only income categories the caller created can be renamed or deleted.
 * Every method is a read-write transaction: resolving the caller may seed their defaults.
 */
@Service
@Transactional
class IncomeCategoryService(
    private val currentUser: CurrentUserService,
    private val categories: IncomeCategoryRepository,
    private val incomes: IncomeRepository,
    private val clock: Clock,
) {
    fun list(identity: VerifiedIdentity): List<IncomeCategory> =
        categories.findAllWithCounts(currentUser.resolve(identity)).map { it.toModel() }

    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): IncomeCategory = categories.findWithCount(currentUser.resolve(identity), id)?.toModel() ?: throw notFound(id)

    fun create(
        identity: VerifiedIdentity,
        command: NameCommand,
    ): IncomeCategory {
        val userId = currentUser.resolve(identity)
        if (categories.existsByUserIdAndNameIgnoreCase(userId, command.name)) {
            throw duplicateName(RESOURCE_LABEL, command.name)
        }
        val now = Instant.now(clock)
        val saved =
            saveOrDuplicate(RESOURCE_LABEL, command.name) {
                categories.saveAndFlush(
                    IncomeCategoryEntity(
                        userId = userId,
                        name = command.name,
                        // The least-used palette colour among the caller's income categories (R-002).
                        color =
                            CategoryPalette.next(
                                categories.colorCounts(userId).associate { it.color to it.count.toInt() },
                            ),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        return IncomeCategory(saved.id, saved.name, isDefault = false, incomeCount = 0, color = saved.color)
    }

    fun rename(
        identity: VerifiedIdentity,
        id: UUID,
        command: NameCommand,
    ): IncomeCategory {
        val userId = currentUser.resolve(identity)
        val category = categories.findByIdAndUserId(id, userId) ?: throw notFound(id)
        refuseDefault(category)
        requireNameFree(userId, command.name, id)
        category.name = command.name
        category.updatedAt = Instant.now(clock)
        saveOrDuplicate(RESOURCE_LABEL, command.name) { categories.saveAndFlush(category) }
        return categories.findWithCount(userId, id)?.toModel() ?: throw notFound(id)
    }

    fun delete(
        identity: VerifiedIdentity,
        id: UUID,
    ) {
        val userId = currentUser.resolve(identity)
        val category = categories.lockByIdAndUserId(id, userId) ?: throw notFound(id)
        refuseDefault(category)
        requireUnused(userId, id)
        try {
            categories.delete(category)
            categories.flush()
        } catch (exception: DataIntegrityViolationException) {
            // Only reachable if an income slipped in despite the row lock (see AccountService).
            throw inUse(RESOURCE_LABEL, id, mapOf(INCOME_COUNT to 1L), exception)
        }
    }

    private fun requireNameFree(
        userId: UUID,
        name: String,
        excluding: UUID,
    ) {
        if (categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, excluding)) {
            throw duplicateName(RESOURCE_LABEL, name)
        }
    }

    private fun requireUnused(
        userId: UUID,
        id: UUID,
    ) {
        val used = incomes.countByUserIdAndCategoryId(userId, id)
        if (used > 0) throw inUse(RESOURCE_LABEL, id, mapOf(INCOME_COUNT to used))
    }

    private fun refuseDefault(category: IncomeCategoryEntity) {
        if (category.isDefault) {
            throw UnprocessableEntityException(
                "${category.name} is a default income category and cannot be changed",
                mapOf("reason" to "default-category"),
            )
        }
    }

    private fun notFound(id: UUID) =
        NotFoundException(
            "Income category $id was not found",
            mapOf("resource" to "income-category", "incomeCategoryId" to id),
        )

    private companion object {
        /** In messages: "An income category named …". */
        const val RESOURCE_LABEL = "income category"
    }
}
