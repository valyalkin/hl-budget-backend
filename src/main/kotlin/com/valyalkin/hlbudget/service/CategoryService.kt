package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.model.Category
import com.valyalkin.hlbudget.model.NameCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.category.CategoryEntity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.category.toModel
import com.valyalkin.hlbudget.repository.spending.SpendingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The caller's categories (US1, US5). Every method is a read-write transaction: resolving the
 * caller may create their user row and default categories on the first call. Defaults are fixed;
 * only categories the caller created can be renamed or deleted.
 */
@Service
@Transactional
class CategoryService(
    private val currentUser: CurrentUserService,
    private val categories: CategoryRepository,
    private val spendings: SpendingRepository,
    private val clock: Clock,
) {
    fun list(identity: VerifiedIdentity): List<Category> =
        categories.findAllWithCounts(currentUser.resolve(identity)).map { it.toModel() }

    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): Category = categories.findWithCount(currentUser.resolve(identity), id)?.toModel() ?: throw notFound(RESOURCE, id)

    fun create(
        identity: VerifiedIdentity,
        command: NameCommand,
    ): Category {
        val userId = currentUser.resolve(identity)
        if (categories.existsByUserIdAndNameIgnoreCase(
                userId,
                command.name,
            )
        ) {
            throw duplicateName(RESOURCE, command.name)
        }
        val now = Instant.now(clock)
        val saved =
            saveOrDuplicate(RESOURCE, command.name) {
                categories.saveAndFlush(
                    CategoryEntity(userId = userId, name = command.name, createdAt = now, updatedAt = now),
                )
            }
        return Category(saved.id, saved.name, isDefault = false, spendingCount = 0)
    }

    fun rename(
        identity: VerifiedIdentity,
        id: UUID,
        command: NameCommand,
    ): Category {
        val userId = currentUser.resolve(identity)
        val category = owned(id, userId)
        refuseDefault(category)
        requireNameFree(userId, command.name, id)
        category.name = command.name
        category.updatedAt = Instant.now(clock)
        saveOrDuplicate(RESOURCE, command.name) { categories.saveAndFlush(category) }
        return withCount(userId, id)
    }

    fun delete(
        identity: VerifiedIdentity,
        id: UUID,
    ) {
        val userId = currentUser.resolve(identity)
        val category = categories.lockByIdAndUserId(id, userId) ?: throw notFound(RESOURCE, id)
        refuseDefault(category)
        requireUnused(userId, id)
        try {
            categories.delete(category)
            categories.flush()
        } catch (exception: DataIntegrityViolationException) {
            // Only reachable if a spending slipped in despite the row lock (see AccountService).
            throw inUse(RESOURCE, id, 1, exception)
        }
    }

    private fun refuseDefault(category: CategoryEntity) {
        if (category.isDefault) {
            throw UnprocessableEntityException(
                "${category.name} is a default category and cannot be changed",
                mapOf("reason" to "default-category"),
            )
        }
    }

    private fun owned(
        id: UUID,
        userId: UUID,
    ): CategoryEntity = categories.findByIdAndUserId(id, userId) ?: throw notFound(RESOURCE, id)

    private fun withCount(
        userId: UUID,
        id: UUID,
    ): Category = categories.findWithCount(userId, id)?.toModel() ?: throw notFound(RESOURCE, id)

    private fun requireNameFree(
        userId: UUID,
        name: String,
        excluding: UUID,
    ) {
        if (categories.existsByUserIdAndNameIgnoreCaseAndIdNot(
                userId,
                name,
                excluding,
            )
        ) {
            throw duplicateName(RESOURCE, name)
        }
    }

    private fun requireUnused(
        userId: UUID,
        id: UUID,
    ) {
        val used = spendings.countByUserIdAndCategoryId(userId, id)
        if (used > 0) throw inUse(RESOURCE, id, used)
    }

    private companion object {
        const val RESOURCE = "category"
    }
}
