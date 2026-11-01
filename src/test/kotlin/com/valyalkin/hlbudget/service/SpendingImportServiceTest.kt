package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.config.HlBudgetProperties
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.account.AccountEntity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryEntity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.spending.SpendingEntity
import com.valyalkin.hlbudget.repository.spending.SpendingInserts
import com.valyalkin.hlbudget.repository.spendingimport.SpendingImportEntity
import com.valyalkin.hlbudget.repository.spendingimport.SpendingImportInserts
import com.valyalkin.hlbudget.repository.spendingimport.SpendingImportRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
import java.util.UUID

/** Feature 004, data-model.md "Write: create an import": all or nothing, existing names only (007), replays. */
class SpendingImportServiceTest {
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val rules =
        SpendingRules(
            clock,
            HlBudgetProperties(HlBudgetProperties.Auth("i", "a", "j", "n"), HlBudgetProperties.Money("CAD"), "UTC"),
        )
    private val currentUser = mockk<CurrentUserService>()
    private val imports = mockk<SpendingImportRepository>()
    private val importInserts = mockk<SpendingImportInserts>()
    private val spendingInserts = mockk<SpendingInserts>()
    private val categories = mockk<CategoryRepository>()
    private val accounts = mockk<AccountRepository>()
    private val names = NamedItemResolver(categories, accounts)
    private val service =
        SpendingImportService(currentUser, imports, importInserts, spendingInserts, names, rules, clock)

    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()
    private val inserted = slot<List<SpendingEntity>>()
    private val receipt = slot<SpendingImportEntity>()

    private fun row(
        date: String = "2026-10-20",
        amount: String = "12.40",
        category: String = "Groceries",
        account: String = "Visa",
        merchant: String = "Corner Bakery",
    ) = ImportRowCommand(LocalDate.parse(date), merchant, BigDecimal(amount), "CAD", category, account)

    private fun idOf(name: String): UUID = UUID.nameUUIDFromBytes(name.lowercase().toByteArray())

    private fun category(name: String) = CategoryEntity(idOf(name), userId, name, true, "#c2410c", now, now, 0)

    private fun account(name: String) = AccountEntity(idOf(name), userId, name, now, now, "#e11d48", 0)

    private fun stored(
        key: String,
        fingerprint: String,
    ) = SpendingImportEntity(
        id = UUID.randomUUID(),
        userId = userId,
        idempotencyKey = key,
        idempotencyFingerprint = fingerprint,
        spendingCount = 1,
        firstDate = LocalDate.parse("2026-10-20"),
        lastDate = LocalDate.parse("2026-10-20"),
        total = BigDecimal("12.40"),
        createdAt = now.minusSeconds(60),
    )

    @BeforeEach
    fun setUp() {
        every { currentUser.resolve(identity) } returns userId
        every { imports.findByUserIdAndIdempotencyKey(userId, any()) } returns null
        every { importInserts.insertUnlessKeyTaken(capture(receipt)) } returns true
        every { spendingInserts.batchInsert(capture(inserted)) } just runs
        every { categories.findAllByUserId(userId) } returns listOf(category("Groceries"), category("Car"))
        every { accounts.findAllByUserId(userId) } returns listOf(account("Visa"), account("Amex"))
    }

    @Test
    fun `valid rows are inserted together and summarised in the receipt`() {
        val result =
            service.create(
                identity,
                listOf(
                    row(date = "2026-10-27", amount = "12.40"),
                    row(date = "2026-09-30", amount = "61.20", category = "Car", merchant = "Lakeshore Fuel, Hwy 7"),
                ),
                idempotencyKey = "k1",
            )

        assertThat(result.replayed).isFalse()
        assertThat(result.import.spendingCount).isEqualTo(2)
        assertThat(result.import.firstDate).isEqualTo(LocalDate.parse("2026-09-30"))
        assertThat(result.import.lastDate).isEqualTo(LocalDate.parse("2026-10-27"))
        assertThat(result.import.total.amount).isEqualByComparingTo("73.60")
        assertThat(result.import.total.currency.currencyCode).isEqualTo("CAD")
        assertThat(receipt.captured.idempotencyKey).isEqualTo("k1")
        assertThat(receipt.captured.idempotencyFingerprint).matches("[0-9a-f]{64}")

        val rows = inserted.captured
        assertThat(rows).extracting<String> { it.merchant }.containsExactly("Corner Bakery", "Lakeshore Fuel, Hwy 7")
        assertThat(rows).allSatisfy {
            assertThat(it.userId).isEqualTo(userId)
            assertThat(it.idempotencyKey).isNull()
            assertThat(it.createdAt).isEqualTo(now)
        }
        assertThat(rows[1].categoryId).isEqualTo(idOf("Car"))
        assertThat(rows[0].accountId).isEqualTo(idOf("Visa"))
    }

    @Test
    fun `names match existing items whatever their case or spaces, read once`() {
        service.create(
            identity,
            listOf(row(account = "Visa"), row(account = "visa "), row(account = "VISA"), row(account = "Amex")),
            idempotencyKey = null,
        )

        verify(exactly = 1) { accounts.findAllByUserId(userId) }
        verify(exactly = 1) { categories.findAllByUserId(userId) }
        assertThat(inserted.captured.map { it.accountId }).containsExactly(
            idOf("Visa"),
            idOf("Visa"),
            idOf("Visa"),
            idOf("Amex"),
        )
    }

    @Test
    fun `unknown names refuse the whole import at each row and nothing is written (feature 007)`() {
        assertThatThrownBy {
            service.create(
                identity,
                listOf(row(), row(category = "Groceriess"), row(account = "Visa Gold"), row(category = "Coffee")),
                idempotencyKey = "k1",
            )
        }.isInstanceOfSatisfying(ValidationException::class.java) { exception ->
            assertThat(exception.violations.map { "${it.pointer} ${it.code} ${it.message}" }).containsExactly(
                "#/spendings/1/categoryName NotFound is not one of your categories",
                "#/spendings/2/accountName NotFound is not one of your accounts",
                "#/spendings/3/categoryName NotFound is not one of your categories",
            )
        }
        verify(exactly = 0) { importInserts.insertUnlessKeyTaken(any()) }
        verify(exactly = 0) { spendingInserts.batchInsert(any()) }
    }

    @Test
    fun `an invalid row refuses the whole import and lists every problem`() {
        assertThatThrownBy {
            service.create(
                identity,
                listOf(row(), row(date = "2026-10-28"), row(amount = "0")),
                idempotencyKey = "k1",
            )
        }.isInstanceOfSatisfying(ValidationException::class.java) { error ->
            assertThat(error.violations)
                .extracting<String> { it.pointer }
                .containsExactly("#/spendings/1/date", "#/spendings/2/amount/amount")
        }

        verify(exactly = 0) { importInserts.insertUnlessKeyTaken(any()) }
        verify(exactly = 0) { spendingInserts.batchInsert(any()) }
    }

    @Test
    fun `no rows at all is refused`() {
        assertThatThrownBy { service.create(identity, emptyList(), idempotencyKey = null) }
            .isInstanceOfSatisfying(ValidationException::class.java) {
                assertThat(it.violations.single().pointer).isEqualTo("#/spendings")
            }
    }

    @Test
    fun `the same key and rows replay the earlier import`() {
        val rows = listOf(row())
        val earlier = stored("k1", rules.importFingerprint(rows))
        every { imports.findByUserIdAndIdempotencyKey(userId, "k1") } returns earlier

        val result = service.create(identity, rows, idempotencyKey = "k1")

        assertThat(result.replayed).isTrue()
        assertThat(result.import.id).isEqualTo(earlier.id)
        verify(exactly = 0) { importInserts.insertUnlessKeyTaken(any()) }
        verify(exactly = 0) { spendingInserts.batchInsert(any()) }
    }

    @Test
    fun `the same key with different rows is refused`() {
        every { imports.findByUserIdAndIdempotencyKey(userId, "k1") } returns
            stored("k1", rules.importFingerprint(listOf(row(amount = "99.99"))))

        assertThatThrownBy { service.create(identity, listOf(row()), idempotencyKey = "k1") }
            .isInstanceOfSatisfying(UnprocessableEntityException::class.java) {
                assertThat(it.details).containsEntry("reason", "idempotency-key-reused")
                assertThat(it.message).isEqualTo("The Idempotency-Key was already used for a different import")
            }
        verify(exactly = 0) { spendingInserts.batchInsert(any()) }
    }

    @Test
    fun `losing the race for a key replays the winner`() {
        val rows = listOf(row())
        val winner = stored("k1", rules.importFingerprint(rows))
        every { importInserts.insertUnlessKeyTaken(any()) } returns false
        every { imports.findByUserIdAndIdempotencyKey(userId, "k1") } returnsMany listOf(null, winner)

        val result = service.create(identity, rows, idempotencyKey = "k1")

        assertThat(result.replayed).isTrue()
        assertThat(result.import.id).isEqualTo(winner.id)
        verify(exactly = 0) { spendingInserts.batchInsert(any()) }
    }

    @Test
    fun `get returns the caller's import and hides anyone else's`() {
        val mine = stored("k1", "f".repeat(64))
        every { imports.findByIdAndUserId(mine.id, userId) } returns mine
        every { imports.findByIdAndUserId(not(mine.id), userId) } returns null

        assertThat(service.get(identity, mine.id).id).isEqualTo(mine.id)
        assertThatThrownBy { service.get(identity, UUID.randomUUID()) }
            .isInstanceOfSatisfying(NotFoundException::class.java) {
                assertThat(it.details).containsEntry("resource", "spending-import")
            }
    }
}
