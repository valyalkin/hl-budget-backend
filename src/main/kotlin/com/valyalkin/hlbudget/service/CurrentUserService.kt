package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.DefaultCategories
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.CurrentUserRepository
import com.valyalkin.hlbudget.repository.CurrentUserRow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Who is calling, as a `users.id`. Every budgeting service method starts here, so every query can
 * filter on the caller and nothing about the caller comes from the request (Principle III).
 */
@Service
class CurrentUserService(
    private val users: CurrentUserRepository,
) {
    /**
     * Joins the caller's transaction, so a rollback there also undoes a first-call insert. On the
     * caller's first budgeting call this also creates their 17 default categories, exactly once
     * even under concurrent first calls (research R-003).
     */
    @Transactional
    fun resolve(identity: VerifiedIdentity): UUID {
        val user = row(identity)
        if (user.categoriesSeededAt == null && users.claimSeeding(user.id)) {
            users.insertDefaultCategories(user.id, DefaultCategories.NAMES)
        }
        return user.id
    }

    private fun row(identity: VerifiedIdentity): CurrentUserRow =
        users.findBySubject(identity.subject)
            ?: run {
                users.insertIfMissing(identity.subject)
                users.findBySubject(identity.subject)
            }
            ?: error("No users row for the caller after inserting it")
}
