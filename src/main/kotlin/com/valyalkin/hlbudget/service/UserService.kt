package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.UpsertResult
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val users: UserRepository,
) {
    /** Creates the caller's record on first use, refreshes it on every later call (FR-012, FR-013). */
    @Transactional
    fun ensureProfile(identity: VerifiedIdentity): UpsertResult = users.upsert(identity)
}
