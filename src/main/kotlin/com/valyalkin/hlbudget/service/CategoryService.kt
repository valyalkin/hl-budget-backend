package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.model.Category
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.category.toModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The caller's categories. Reads are read-write transactions too: resolving the caller may create
 * their user row and their default categories on the first call.
 */
@Service
@Transactional(readOnly = true)
class CategoryService(
    private val currentUser: CurrentUserService,
    private val categories: CategoryRepository,
) {
    @Transactional
    fun list(identity: VerifiedIdentity): List<Category> =
        categories.findAllWithCounts(currentUser.resolve(identity)).map { it.toModel() }

    @Transactional
    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): Category = categories.findWithCount(currentUser.resolve(identity), id)?.toModel() ?: throw notFound(id)

    private fun notFound(id: UUID) =
        NotFoundException("Category $id was not found", mapOf("resource" to "category", "categoryId" to id))
}
