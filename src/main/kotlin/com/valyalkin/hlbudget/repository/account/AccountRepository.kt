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
     * `FOR UPDATE`, not JPA's PESSIMISTIC_WRITE (which Postgres runs as `FOR NO KEY UPDATE`): it
     * waits for any transaction inserting a spending against this account, so the in-use count
     * read next is final (research R-005).
     */
    @Query(value = "SELECT * FROM accounts WHERE id = :id AND user_id = :userId FOR UPDATE", nativeQuery = true)
    fun lockByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): AccountEntity?
}
