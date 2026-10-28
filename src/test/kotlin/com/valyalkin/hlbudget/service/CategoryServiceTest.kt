package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.ConflictException
import com.valyalkin.hlbudget.error.DuplicateException
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import com.valyalkin.hlbudget.model.NameCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.category.CategoryEntity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.category.CategoryRow
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

class CategoryServiceTest {
    private val currentUser = mockk<CurrentUserService>()
    private val categories = mockk<CategoryRepository>(relaxed = true)
    private val spendings = mockk<SpendingRepository>()
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val service = CategoryService(currentUser, categories, spendings, Clock.fixed(now, ZoneOffset.UTC))
    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()
    private val id = UUID.randomUUID()

    private fun entity(
        name: String = "Coffee",
        isDefault: Boolean = false,
    ) = CategoryEntity(id, userId, name, isDefault, now.minusSeconds(60), now.minusSeconds(60), 0)

    @BeforeEach
    fun caller() {
        every { currentUser.resolve(identity) } returns userId
    }

    @Test
    fun `create makes a custom category`() {
        every { categories.existsByUserIdAndNameIgnoreCase(userId, "Coffee") } returns false
        val saved = slot<CategoryEntity>()
        every { categories.saveAndFlush(capture(saved)) } answers { saved.captured }

        val category = service.create(identity, NameCommand("Coffee"))

        assertThat(category.isDefault).isFalse()
        assertThat(category.spendingCount).isZero()
        assertThat(saved.captured.userId).isEqualTo(userId)
    }

    @Test
    fun `a default's name counts as taken`() {
        every { categories.existsByUserIdAndNameIgnoreCase(userId, "groceries") } returns true

        assertThatThrownBy { service.create(identity, NameCommand("groceries")) }
            .isInstanceOf(DuplicateException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "duplicate-name", "name" to "groceries"))
    }

    @Test
    fun `a lost uniqueness race on create or rename is a duplicate`() {
        every { categories.existsByUserIdAndNameIgnoreCase(userId, "Coffee") } returns false
        every { categories.saveAndFlush(any()) } throws DataIntegrityViolationException("ux")

        assertThatThrownBy { service.create(identity, NameCommand("Coffee")) }
            .isInstanceOf(DuplicateException::class.java)
    }

    @Test
    fun `a default category cannot be renamed or deleted, before any other check`() {
        every { categories.findByIdAndUserId(id, userId) } returns entity("Groceries", isDefault = true)
        every { categories.lockByIdAndUserId(id, userId) } returns entity("Groceries", isDefault = true)

        listOf(
            { service.rename(identity, id, NameCommand("Food")) },
            { service.delete(identity, id) },
        ).forEach { call ->
            assertThatThrownBy { call() }
                .isInstanceOf(UnprocessableEntityException::class.java)
                .hasMessage("Groceries is a default category and cannot be changed")
                .hasFieldOrPropertyWithValue("details", mapOf("reason" to "default-category"))
        }
        verify(exactly = 0) { spendings.countByUserIdAndCategoryId(any(), any()) }
        verify(exactly = 0) { categories.delete(any()) }
    }

    @Test
    fun `a custom category is renamed`() {
        val existing = entity()
        every { categories.findByIdAndUserId(id, userId) } returns existing
        every { categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, "Cafés", id) } returns false
        every { categories.saveAndFlush(existing) } returns existing
        every { categories.findWithCount(userId, id) } answers { CategoryRow(id, existing.name, false, 4) }

        val renamed = service.rename(identity, id, NameCommand("Cafés"))

        assertThat(renamed.name).isEqualTo("Cafés")
        assertThat(renamed.spendingCount).isEqualTo(4)
        assertThat(existing.updatedAt).isEqualTo(now)
    }

    @Test
    fun `rename to another category's name is a duplicate`() {
        every { categories.findByIdAndUserId(id, userId) } returns entity()
        every { categories.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, "Taxi", id) } returns true

        assertThatThrownBy { service.rename(identity, id, NameCommand("Taxi")) }
            .isInstanceOf(DuplicateException::class.java)
    }

    @Test
    fun `a custom category in use cannot be deleted`() {
        every { categories.lockByIdAndUserId(id, userId) } returns entity()
        every { spendings.countByUserIdAndCategoryId(userId, id) } returns 1

        assertThatThrownBy { service.delete(identity, id) }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("details", mapOf("reason" to "in-use", "spendingCount" to 1L))
        verify(exactly = 0) { categories.delete(any()) }
    }

    @Test
    fun `a foreign key failure on delete is in use too`() {
        every { categories.lockByIdAndUserId(id, userId) } returns entity()
        every { spendings.countByUserIdAndCategoryId(userId, id) } returns 0
        every { categories.flush() } throws DataIntegrityViolationException("fk_spendings_category")

        assertThatThrownBy { service.delete(identity, id) }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `an unused custom category is deleted`() {
        val existing = entity()
        every { categories.lockByIdAndUserId(id, userId) } returns existing
        every { spendings.countByUserIdAndCategoryId(userId, id) } returns 0

        service.delete(identity, id)

        verify { categories.delete(existing) }
    }

    @Test
    fun `a missing or foreign category is not found`() {
        every { categories.findByIdAndUserId(id, userId) } returns null
        every { categories.lockByIdAndUserId(id, userId) } returns null
        every { categories.findWithCount(userId, id) } returns null

        assertThatThrownBy { service.get(identity, id) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.rename(identity, id, NameCommand("x")) }
            .isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { service.delete(identity, id) }.isInstanceOf(NotFoundException::class.java)
    }
}
