package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.ConflictException
import com.valyalkin.hlbudget.error.DuplicateException
import com.valyalkin.hlbudget.error.NotFoundException
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

/** The caller's accounts (US2). Every lookup is scoped to the caller; anyone else's is not found. */
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
    ): Account = accounts.findWithCount(currentUser.resolve(identity), id)?.toModel() ?: throw notFound(id)

    @Transactional
    fun create(
        identity: VerifiedIdentity,
        command: NameCommand,
    ): Account {
        val userId = currentUser.resolve(identity)
        if (accounts.existsByUserIdAndNameIgnoreCase(userId, command.name)) throw duplicate(command.name)
        val now = Instant.now(clock)
        val saved =
            saveOrDuplicate(command.name) {
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
        val account = accounts.findByIdAndUserId(id, userId) ?: throw notFound(id)
        if (accounts.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, command.name, id)) throw duplicate(command.name)
        account.name = command.name
        account.updatedAt = Instant.now(clock)
        saveOrDuplicate(command.name) { accounts.saveAndFlush(account) }
        return accounts.findWithCount(userId, id)?.toModel() ?: throw notFound(id)
    }

    @Transactional
    fun delete(
        identity: VerifiedIdentity,
        id: UUID,
    ) {
        val userId = currentUser.resolve(identity)
        val account = accounts.lockByIdAndUserId(id, userId) ?: throw notFound(id)
        val used = spendings.countByUserIdAndAccountId(userId, id)
        if (used > 0) throw inUse(id, used)
        try {
            accounts.delete(account)
            accounts.flush()
        } catch (exception: DataIntegrityViolationException) {
            // Only reachable if a spending slipped in despite the row lock; the statement failed, so
            // the transaction cannot count again. At least one spending uses the account.
            throw inUse(id, 1, exception)
        }
    }

    private fun <T> saveOrDuplicate(
        name: String,
        save: () -> T,
    ): T =
        try {
            save()
        } catch (exception: DataIntegrityViolationException) {
            throw duplicate(name, exception)
        }

    private fun notFound(id: UUID) =
        NotFoundException("Account $id was not found", mapOf("resource" to "account", "accountId" to id))

    private fun duplicate(
        name: String,
        cause: Throwable? = null,
    ) = DuplicateException(
        "An account named \"$name\" already exists",
        mapOf("reason" to "duplicate-name", "name" to name),
        cause,
    )

    private fun inUse(
        id: UUID,
        count: Long,
        cause: Throwable? = null,
    ) = ConflictException(
        "Account $id is used by $count spendings",
        mapOf("reason" to "in-use", "spendingCount" to count),
        cause,
    )
}
