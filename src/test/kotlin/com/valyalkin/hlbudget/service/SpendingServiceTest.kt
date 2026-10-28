package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.config.HlBudgetProperties
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountEntity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryEntity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.spending.SpendingEntity
import com.valyalkin.hlbudget.repository.spending.SpendingInserts
import com.valyalkin.hlbudget.repository.spending.SpendingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class SpendingServiceTest {
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val rules =
        SpendingRules(
            clock,
            HlBudgetProperties(HlBudgetProperties.Auth("i", "a", "j", "n"), HlBudgetProperties.Money("CAD"), "UTC"),
        )
    private val currentUser = mockk<CurrentUserService>()
    private val spendings = mockk<SpendingRepository>(relaxed = true)
    private val inserts = mockk<SpendingInserts>()
    private val categories = mockk<CategoryRepository>()
    private val accounts = mockk<AccountRepository>()
    private val service = SpendingService(currentUser, spendings, inserts, categories, accounts, rules, clock)

    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()
    private val category = CategoryEntity(UUID.randomUUID(), userId, "Groceries", true, now, now, 0)
    private val account = AccountEntity(UUID.randomUUID(), userId, "Amex", now, now, 0)
    private val command =
        SpendingCommand(LocalDate.parse("2026-10-20"), "Loblaws", BigDecimal("84.37"), "CAD", category.id, account.id)

    private fun stored(
        key: String? = null,
        fingerprint: String? = null,
    ) = SpendingEntity(
        id = UUID.randomUUID(),
        userId = userId,
        date = command.date,
        merchant = command.merchant,
        amount = command.amount,
        categoryId = category.id,
        accountId = account.id,
        idempotencyKey = key,
        idempotencyFingerprint = fingerprint,
        createdAt = now.minusSeconds(60),
        updatedAt = now.minusSeconds(60),
        version = 0,
    )

    @BeforeEach
    fun caller() {
        every { currentUser.resolve(identity) } returns userId
        every { categories.findByIdAndUserId(category.id, userId) } returns category
        every { accounts.findByIdAndUserId(account.id, userId) } returns account
    }

    @Test
    fun `create inserts the spending and returns it with names and the configured currency`() {
        val inserted = slot<SpendingEntity>()
        every { inserts.insertUnlessKeyTaken(capture(inserted)) } returns true
        every { spendings.findById(any()) } answers { Optional.of(inserted.captured) }

        val result = service.create(identity, command, idempotencyKey = null)

        assertThat(result.replayed).isFalse()
        assertThat(result.spending.category.name).isEqualTo("Groceries")
        assertThat(result.spending.account.name).isEqualTo("Amex")
        assertThat(result.spending.amount.currency.currencyCode).isEqualTo("CAD")
        assertThat(inserted.captured.createdAt).isEqualTo(now)
        assertThat(inserted.captured.idempotencyFingerprint).isNull()
    }

    @Test
    fun `unknown references and rule violations are reported together`() {
        every { categories.findByIdAndUserId(any(), userId) } returns null
        every { accounts.findByIdAndUserId(any(), userId) } returns null
        val bad = command.copy(amount = BigDecimal.ZERO, date = LocalDate.parse("2026-10-28"))

        assertThatThrownBy { service.create(identity, bad, null) }
            .isInstanceOfSatisfying(ValidationException::class.java) { exception ->
                assertThat(exception.violations.map { it.pointer })
                    .containsExactly("#/date", "#/amount/amount", "#/categoryId", "#/accountId")
                assertThat(exception.violations.filter { it.pointer.endsWith("Id") }.map { it.code })
                    .containsOnly("NotFound")
            }
        verify(exactly = 0) { inserts.insertUnlessKeyTaken(any()) }
    }

    @Test
    fun `the same key with the same body replays the stored spending`() {
        val existing = stored("k1", rules.fingerprint(command))
        every { spendings.findByUserIdAndIdempotencyKey(userId, "k1") } returns existing

        val result = service.create(identity, command, "k1")

        assertThat(result.replayed).isTrue()
        assertThat(result.spending.id).isEqualTo(existing.id)
        verify(exactly = 0) { inserts.insertUnlessKeyTaken(any()) }
    }

    @Test
    fun `the same key with a different body is refused`() {
        every { spendings.findByUserIdAndIdempotencyKey(userId, "k1") } returns stored("k1", "f".repeat(64))

        assertThatThrownBy { service.create(identity, command, "k1") }
            .isInstanceOf(UnprocessableEntityException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "idempotency-key-reused"))
    }

    @Test
    fun `losing the race for a key replays the winner`() {
        val winner = stored("k1", rules.fingerprint(command))
        every { spendings.findByUserIdAndIdempotencyKey(userId, "k1") } returnsMany listOf(null, winner)
        every { inserts.insertUnlessKeyTaken(any()) } returns false

        val result = service.create(identity, command, "k1")

        assertThat(result.replayed).isTrue()
        assertThat(result.spending.id).isEqualTo(winner.id)
    }

    @Test
    fun `update replaces the details, keeps createdAt and the key, and sets updatedAt`() {
        val existing = stored("k1", "f".repeat(64))
        val createdAt = existing.createdAt
        every { spendings.findByIdAndUserId(existing.id, userId) } returns existing
        every { spendings.saveAndFlush(existing) } returns existing

        val updated =
            service.update(
                identity,
                existing.id,
                command.copy(merchant = "Metro", amount = BigDecimal("9.99")),
            )

        assertThat(updated.merchant).isEqualTo("Metro")
        assertThat(updated.amount.amount).isEqualByComparingTo("9.99")
        assertThat(updated.createdAt).isEqualTo(createdAt)
        assertThat(updated.updatedAt).isEqualTo(now)
        assertThat(existing.idempotencyKey).isEqualTo("k1")
    }

    @Test
    fun `get, update and delete of a missing or foreign spending are not found`() {
        val id = UUID.randomUUID()
        every { spendings.findByIdAndUserId(id, userId) } returns null

        assertThatThrownBy { service.get(identity, id) }
            .isInstanceOf(NotFoundException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("resource" to "spending", "spendingId" to id))
        assertThatThrownBy { service.update(identity, id, command) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.delete(identity, id) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `delete removes the caller's spending`() {
        val existing = stored()
        every { spendings.findByIdAndUserId(existing.id, userId) } returns existing

        service.delete(identity, existing.id)

        verify { spendings.delete(existing) }
    }
}
