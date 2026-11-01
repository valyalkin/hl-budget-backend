package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.ConflictException
import com.valyalkin.hlbudget.error.DuplicateException
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.model.NameCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.category.ColorCount
import com.valyalkin.hlbudget.repository.income.IncomeRepository
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryEntity
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRepository
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRow
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

/** Feature 009, research R-002: income categories, their colours and the rules on the defaults. */
class IncomeCategoryServiceTest {
    private val currentUser = mockk<CurrentUserService>()
    private val categories = mockk<IncomeCategoryRepository>(relaxed = true)
    private val incomes = mockk<IncomeRepository>()
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val service = IncomeCategoryService(currentUser, categories, incomes, Clock.fixed(now, ZoneOffset.UTC))
    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()
    private val id = UUID.randomUUID()

    private fun entity(
        name: String = "Freelance",
        isDefault: Boolean = false,
    ) = IncomeCategoryEntity(id, userId, name, isDefault, "#e11d48", now.minusSeconds(60), now.minusSeconds(60), 0)

    private fun colorCounts(vararg colors: String): List<ColorCount> =
        colors.toList().groupingBy { it }.eachCount().map { (color, count) ->
            object : ColorCount {
                override val color = color
                override val count = count.toLong()
            }
        }

    @BeforeEach
    fun caller() {
        every { currentUser.resolve(identity) } returns userId
    }

    @Test
    fun `a new one gets the least used colour among income categories only`() {
        every { categories.existsByUserIdAndNameIgnoreCase(userId, "Freelance") } returns false
        every { categories.colorCounts(userId) } returns colorCounts("#1e5b45", "#1d4ed8", "#e11d48")
        val saved = slot<IncomeCategoryEntity>()
        every { categories.saveAndFlush(capture(saved)) } answers { saved.captured }

        val created = service.create(identity, NameCommand("Freelance"))

        assertThat(created.color).isEqualTo("#0891b2")
        assertThat(created.isDefault).isFalse()
        assertThat(created.incomeCount).isZero()
        assertThat(saved.captured.createdAt).isEqualTo(now)
    }

    @Test
    fun `a taken name is refused before and after the insert`() {
        every { categories.existsByUserIdAndNameIgnoreCase(userId, "Salary") } returns true
        assertThatThrownBy {
            service.create(
                identity,
                NameCommand("Salary"),
            )
        }.isInstanceOf(DuplicateException::class.java)

        every { categories.existsByUserIdAndNameIgnoreCase(userId, "Gifts") } returns false
        every { categories.colorCounts(userId) } returns emptyList()
        every { categories.saveAndFlush(any()) } throws DataIntegrityViolationException("ux")
        assertThatThrownBy { service.create(identity, NameCommand("Gifts")) }
            .isInstanceOf(DuplicateException::class.java)
            .hasMessageContaining("An income category named \"Gifts\"")
    }

    @Test
    fun `defaults are not renamed or deleted`() {
        every { categories.findByIdAndUserId(id, userId) } returns entity("Salary", isDefault = true)
        every { categories.lockByIdAndUserId(id, userId) } returns entity("Salary", isDefault = true)

        assertThatThrownBy { service.rename(identity, id, NameCommand("Wages")) }
            .isInstanceOf(UnprocessableEntityException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "default-category"))
        assertThatThrownBy { service.delete(identity, id) }.isInstanceOf(UnprocessableEntityException::class.java)
        verify(exactly = 0) { categories.delete(any()) }
    }

    @Test
    fun `rename keeps the colour and returns the current count`() {
        val existing = entity()
        every { categories.findByIdAndUserId(id, userId) } returns existing
        every { categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, "Gifts", id) } returns false
        every { categories.findWithCount(userId, id) } returns IncomeCategoryRow(id, "Gifts", false, 2, "#e11d48")
        every { categories.saveAndFlush(existing) } returns existing

        val renamed = service.rename(identity, id, NameCommand("Gifts"))

        assertThat(existing.name).isEqualTo("Gifts")
        assertThat(renamed.color).isEqualTo("#e11d48")
        assertThat(renamed.incomeCount).isEqualTo(2)
    }

    @Test
    fun `delete is refused while income uses it, and an unknown one is not found`() {
        every { categories.lockByIdAndUserId(id, userId) } returns entity()
        every { incomes.countByUserIdAndCategoryId(userId, id) } returns 3

        assertThatThrownBy { service.delete(identity, id) }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "in-use", "incomeCount" to 3L))

        every { categories.lockByIdAndUserId(id, userId) } returns null
        assertThatThrownBy { service.delete(identity, id) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Income category $id was not found")
    }

    @Test
    fun `an unused custom one is deleted, a foreign key failure is reported as in use`() {
        every { categories.lockByIdAndUserId(id, userId) } returns entity()
        every { incomes.countByUserIdAndCategoryId(userId, id) } returns 0

        service.delete(identity, id)
        verify(exactly = 1) { categories.delete(any()) }

        every { categories.flush() } throws DataIntegrityViolationException("fk_incomes_category")
        assertThatThrownBy { service.delete(identity, id) }.isInstanceOf(ConflictException::class.java)
    }
}
