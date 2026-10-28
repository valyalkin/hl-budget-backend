package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.Account
import com.valyalkin.hlbudget.model.NameCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountEntity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.account.toModel
import com.valyalkin.hlbudget.repository.spending.SpendingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The caller's accounts (US2). Every lookup is scoped to the caller; anyone else's is not found.
 * The caller's only account cannot be deleted (feature 003, FR-086).
 */
@Service
@Transactional(readOnly = true)
class AccountService(
    private val currentUser: CurrentUserService,
    private val accounts: AccountRepository,
    private val spendings: SpendingRepository,
    private val clock: Clock,
) {
    @Transactional
    fun list(identity: VerifiedIdentity): List<Account> =
        accounts.findAllWithCounts(currentUser.resolve(identity)).map { it.toModel() }

    @Transactional
    fun get(
        identity: VerifiedIdentity,
        id: UUID,
    ): Account = accounts.findWithCount(currentUser.resolve(identity), id)?.toModel() ?: throw notFound(RESOURCE, id)

    @Transactional
    fun create(
        identity: VerifiedIdentity,
        command: NameCommand,
    ): Account {
        val userId = currentUser.resolve(identity)
        if (accounts.existsByUserIdAndNameIgnoreCase(userId, command.name)) throw duplicateName(RESOURCE, command.name)
        val now = Instant.now(clock)
        val saved =
            saveOrDuplicate(RESOURCE, command.name) {
                accounts.saveAndFlush(
                    AccountEntity(userId = userId, name = command.name, createdAt = now, updatedAt = now),
                )
            }
        return Account(saved.id, saved.name, spendingCount = 0)
    }

    @Transactional
    fun rename(
        identity: VerifiedIdentity,
        id: UUID,
        command: NameCommand,
    ): Account {
        val userId = currentUser.resolve(identity)
        val account = owned(id, userId)
        requireNameFree(userId, command.name, id)
        account.name = command.name
        account.updatedAt = Instant.now(clock)
        saveOrDuplicate(RESOURCE, command.name) { accounts.saveAndFlush(account) }
        return withCount(userId, id)
    }

    @Transactional
    fun delete(
        identity: VerifiedIdentity,
        id: UUID,
    ) {
        val userId = currentUser.resolve(identity)
        val account = deletable(userId, id)
        try {
            accounts.delete(account)
            accounts.flush()
        } catch (exception: DataIntegrityViolationException) {
            // Only reachable if a spending slipped in despite the row lock; the statement failed, so
            // the transaction cannot count again. At least one spending uses the account.
            throw inUse(RESOURCE, id, 1, exception)
        }
    }

    /**
     * The account, once it is known that it can go: it is the caller's, no spending uses it, and it
     * is not their only account (FR-086). The lock on all their accounts makes both checks final.
     */
    private fun deletable(
        userId: UUID,
        id: UUID,
    ): AccountEntity {
        val owned = accounts.lockAllByUserId(userId)
        val account = owned.firstOrNull { it.id == id } ?: throw notFound(RESOURCE, id)
        requireUnused(userId, id)
        if (owned.size == 1) throw lastAccount(id)
        return account
    }

    private fun owned(
        id: UUID,
        userId: UUID,
    ): AccountEntity = accounts.findByIdAndUserId(id, userId) ?: throw notFound(RESOURCE, id)

    private fun withCount(
        userId: UUID,
        id: UUID,
    ): Account = accounts.findWithCount(userId, id)?.toModel() ?: throw notFound(RESOURCE, id)

    private fun requireNameFree(
        userId: UUID,
        name: String,
        excluding: UUID,
    ) {
        if (accounts.existsByUserIdAndNameIgnoreCaseAndIdNot(
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
        val used = spendings.countByUserIdAndAccountId(userId, id)
        if (used > 0) throw inUse(RESOURCE, id, used)
    }

    private companion object {
        const val RESOURCE = "account"
    }
}
