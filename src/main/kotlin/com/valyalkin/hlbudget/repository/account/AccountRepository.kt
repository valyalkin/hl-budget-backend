package com.valyalkin.hlbudget.repository.account

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** An account with the number of the owner's spendings that use it, read in one query. */
data class AccountRow(
    val id: UUID,
    val name: String,
    val spendingCount: Long,
)

interface AccountRepository : JpaRepository<AccountEntity, UUID> {
    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.account.AccountRow(a.id, a.name, " +
            "(SELECT count(s) FROM SpendingEntity s WHERE s.userId = a.userId AND s.accountId = a.id)) " +
            "FROM AccountEntity a WHERE a.userId = :userId ORDER BY lower(a.name), a.id",
    )
    fun findAllWithCounts(userId: UUID): List<AccountRow>

    @Query(
        "SELECT new com.valyalkin.hlbudget.repository.account.AccountRow(a.id, a.name, " +
            "(SELECT count(s) FROM SpendingEntity s WHERE s.userId = a.userId AND s.accountId = a.id)) " +
            "FROM AccountEntity a WHERE a.userId = :userId AND a.id = :id",
    )
    fun findWithCount(
        userId: UUID,
        id: UUID,
    ): AccountRow?

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): AccountEntity?

    fun findAllByUserId(userId: UUID): List<AccountEntity>

    fun existsByUserIdAndNameIgnoreCase(
        userId: UUID,
        name: String,
    ): Boolean

    fun existsByUserIdAndNameIgnoreCaseAndIdNot(
        userId: UUID,
        name: String,
        id: UUID,
    ): Boolean

    /**
     * Locks every one of the caller's accounts, in id order so two deletes cannot deadlock.
     * `FOR UPDATE`, not JPA's PESSIMISTIC_WRITE (which Postgres runs as `FOR NO KEY UPDATE`): it
     * also waits for any transaction inserting a spending against these accounts, so the in-use
     * count read next is final (research R-005 of feature 002). Held
     * until the end of the transaction, it makes "is this the last account?" final: a concurrent
     * delete of another of the caller's accounts waits here (research R-006).
     */
    @Query(
        value = "SELECT * FROM accounts WHERE user_id = :userId ORDER BY id FOR UPDATE",
        nativeQuery = true,
    )
    fun lockAllByUserId(userId: UUID): List<AccountEntity>
}
