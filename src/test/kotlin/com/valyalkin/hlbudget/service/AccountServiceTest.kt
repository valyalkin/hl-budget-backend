package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.ConflictException
import com.valyalkin.hlbudget.error.DuplicateException
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.model.NameCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountEntity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.account.AccountRow
import com.valyalkin.hlbudget.repository.spending.SpendingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AccountServiceTest {
    private val currentUser = mockk<CurrentUserService>()
    private val accounts = mockk<AccountRepository>(relaxed = true)
    private val spendings = mockk<SpendingRepository>()
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val service = AccountService(currentUser, accounts, spendings, Clock.fixed(now, ZoneOffset.UTC))
    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()
    private val accountId = UUID.randomUUID()

    private fun entity(name: String = "Amex") =
        AccountEntity(
            id = accountId,
            userId = userId,
            name = name,
            createdAt = now.minusSeconds(60),
            updatedAt = now.minusSeconds(60),
            version = 0,
        )

    @BeforeEach
    fun caller() {
        every { currentUser.resolve(identity) } returns userId
    }

    @Test
    fun `create saves the trimmed name for the caller`() {
        every { accounts.existsByUserIdAndNameIgnoreCase(userId, "Amex") } returns false
        val saved = slot<AccountEntity>()
        every { accounts.saveAndFlush(capture(saved)) } answers { saved.captured }

        val account = service.create(identity, NameCommand("Amex"))

        assertThat(account.name).isEqualTo("Amex")
        assertThat(account.spendingCount).isZero()
        assertThat(saved.captured.userId).isEqualTo(userId)
        assertThat(saved.captured.createdAt).isEqualTo(now)
    }

    @Test
    fun `create refuses a duplicate name found by the pre-check`() {
        every { accounts.existsByUserIdAndNameIgnoreCase(userId, "amex") } returns true

        assertThatThrownBy { service.create(identity, NameCommand("amex")) }
            .isInstanceOf(DuplicateException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "duplicate-name", "name" to "amex"))
    }

    @Test
    fun `create turns a lost uniqueness race into the same duplicate`() {
        every { accounts.existsByUserIdAndNameIgnoreCase(userId, "Amex") } returns false
        every { accounts.saveAndFlush(any()) } throws DataIntegrityViolationException("ux_accounts_user_lower_name")

        assertThatThrownBy { service.create(identity, NameCommand("Amex")) }
            .isInstanceOf(DuplicateException::class.java)
    }

    @Test
    fun `rename allows a change of letter case only, because the item itself is excluded`() {
        val existing = entity()
        every { accounts.findByIdAndUserId(accountId, userId) } returns existing
        every { accounts.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, "AMEX", accountId) } returns false
        every { accounts.saveAndFlush(existing) } returns existing
        every { accounts.findWithCount(userId, accountId) } answers { AccountRow(accountId, existing.name, 3) }

        val renamed = service.rename(identity, accountId, NameCommand("AMEX"))

        assertThat(renamed.name).isEqualTo("AMEX")
        assertThat(renamed.spendingCount).isEqualTo(3)
        assertThat(existing.updatedAt).isEqualTo(now)
    }

    @Test
    fun `rename refuses another account's name`() {
        every { accounts.findByIdAndUserId(accountId, userId) } returns entity()
        every { accounts.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, "Cash", accountId) } returns true

        assertThatThrownBy { service.rename(identity, accountId, NameCommand("Cash")) }
            .isInstanceOf(DuplicateException::class.java)
    }

    @Test
    fun `get, rename and delete of a missing or foreign id are not found`() {
        every { accounts.findWithCount(userId, accountId) } returns null
        every { accounts.findByIdAndUserId(accountId, userId) } returns null
        every { accounts.lockByIdAndUserId(accountId, userId) } returns null

        assertThatThrownBy { service.get(identity, accountId) }
            .isInstanceOf(NotFoundException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("resource" to "account", "accountId" to accountId))
        assertThatThrownBy { service.rename(identity, accountId, NameCommand("x")) }
            .isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.delete(identity, accountId) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `delete is refused while spendings use the account, and nothing is deleted`() {
        every { accounts.lockByIdAndUserId(accountId, userId) } returns entity()
        every { spendings.countByUserIdAndAccountId(userId, accountId) } returns 2

        assertThatThrownBy { service.delete(identity, accountId) }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "in-use", "spendingCount" to 2L))
        verify(exactly = 0) { accounts.delete(any()) }
    }

    @Test
    fun `a foreign key failure on delete is reported as in use too`() {
        val existing = entity()
        every { accounts.lockByIdAndUserId(accountId, userId) } returns existing
        every { spendings.countByUserIdAndAccountId(userId, accountId) } returns 0
        every { accounts.flush() } throws DataIntegrityViolationException("fk_spendings_account")

        assertThatThrownBy { service.delete(identity, accountId) }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "in-use", "spendingCount" to 1L))
    }

    @Test
    fun `an unused account is deleted`() {
        val existing = entity()
        every { accounts.lockByIdAndUserId(accountId, userId) } returns existing
        every { spendings.countByUserIdAndAccountId(userId, accountId) } returns 0

        service.delete(identity, accountId)

        verify { accounts.delete(existing) }
    }

    @Test
    fun `list maps every row`() {
        every { accounts.findAllWithCounts(userId) } returns listOf(AccountRow(accountId, "Amex", 1))

        assertThat(service.list(identity)).singleElement().satisfies({ assertThat(it.spendingCount).isEqualTo(1) })
    }
}
