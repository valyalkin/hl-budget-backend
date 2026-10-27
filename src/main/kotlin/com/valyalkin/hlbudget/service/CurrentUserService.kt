package com.valyalkin.hlbudget.service

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
    /** Joins the caller's transaction: a rollback there also undoes a first-call insert. */
    @Transactional
    fun resolve(identity: VerifiedIdentity): UUID = row(identity).id

    private fun row(identity: VerifiedIdentity): CurrentUserRow =
        users.findBySubject(identity.subject)
            ?: run {
                users.insertIfMissing(identity.subject)
                users.findBySubject(identity.subject)
            }
            ?: error("No users row for the caller after inserting it")
}
