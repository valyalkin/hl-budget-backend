package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.repository.NamedItemInserts
import com.valyalkin.hlbudget.repository.NamedItemRow
import com.valyalkin.hlbudget.repository.account.AccountEntity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryEntity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Feature 003, research R-004: a spending's category and account by id or by name. */
class NamedItemResolverTest {
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val categories = mockk<CategoryRepository>()
    private val accounts = mockk<AccountRepository>()
    private val inserts = mockk<NamedItemInserts>()
    private val resolver = NamedItemResolver(categories, accounts, inserts, Clock.fixed(now, ZoneOffset.UTC))

    private val userId = UUID.randomUUID()
    private val groceries = CategoryEntity(UUID.randomUUID(), userId, "Groceries", true, now, now, 0)
    private val amex = AccountEntity(UUID.randomUUID(), userId, "Amex", now, now, 0)

    private fun command(
        categoryId: UUID? = null,
        accountId: UUID? = null,
        categoryName: String? = null,
        accountName: String? = null,
    ) = SpendingCommand(
        LocalDate.parse("2026-10-20"),
        "Loblaws",
        BigDecimal("84.37"),
        "CAD",
        categoryId,
        accountId,
        categoryName,
        accountName,
    )

    private fun pointersOf(block: () -> Unit): List<String> {
        var pointers = emptyList<String>()
        assertThatThrownBy(block).isInstanceOfSatisfying(ValidationException::class.java) { exception ->
            pointers = exception.violations.map { "${it.pointer} ${it.code}" }
        }
        return pointers
    }

    @Test
    fun `ids of the caller's own items are used as they are`() {
        every { categories.findByIdAndUserId(groceries.id, userId) } returns groceries
        every { accounts.findByIdAndUserId(amex.id, userId) } returns amex

        val (category, account) = resolver.resolve(userId, command(groceries.id, amex.id), emptyList())

        assertThat(category).isEqualTo(Ref(groceries.id, "Groceries"))
        assertThat(account).isEqualTo(Ref(amex.id, "Amex"))
        verify(exactly = 0) { inserts.findOrCreateCategory(any(), any(), any()) }
    }

    @Test
    fun `a name is trimmed and reused or created through the find-or-create insert`() {
        val coffee = NamedItemRow(UUID.randomUUID(), "Coffee")
        val visa = NamedItemRow(UUID.randomUUID(), "Visa")
        every { inserts.findOrCreateCategory(userId, "coffee", now) } returns coffee
        every { inserts.findOrCreateAccount(userId, "Visa", now) } returns visa

        val (category, account) =
            resolver.resolve(userId, command(categoryName = " coffee ", accountName = "Visa"), emptyList())

        assertThat(category).isEqualTo(Ref(coffee.id, "Coffee"))
        assertThat(account).isEqualTo(Ref(visa.id, "Visa"))
    }

    @Test
    fun `exactly one of id and name is required for each`() {
        assertThat(pointersOf { resolver.resolve(userId, command(), emptyList()) })
            .containsExactly("#/categoryId ExactlyOne", "#/accountId ExactlyOne")
        every { categories.findByIdAndUserId(groceries.id, userId) } returns groceries
        assertThat(
            pointersOf {
                resolver.resolve(
                    userId,
                    command(groceries.id, categoryName = "Groceries", accountName = "Visa"),
                    emptyList(),
                )
            },
        ).containsExactly("#/categoryId ExactlyOne")
    }

    @Test
    fun `a blank or too long name is invalid`() {
        assertThat(
            pointersOf {
                resolver.resolve(
                    userId,
                    command(categoryName = "   ", accountName = "a".repeat(51)),
                    emptyList(),
                )
            },
        ).containsExactly("#/categoryName NotBlank", "#/accountName Size")
    }

    @Test
    fun `another user's or an unknown id is NotFound`() {
        every { categories.findByIdAndUserId(any(), userId) } returns null
        every { accounts.findByIdAndUserId(any(), userId) } returns null

        assertThat(pointersOf { resolver.resolve(userId, command(UUID.randomUUID(), UUID.randomUUID()), emptyList()) })
            .containsExactly("#/categoryId NotFound", "#/accountId NotFound")
    }

    @Test
    fun `nothing is created when any other field is invalid, and every problem is reported`() {
        every { accounts.findByIdAndUserId(any(), userId) } returns null
        val earlier = listOf(FieldViolation("#/date", "date", "NotFuture", "must be today or earlier"))

        assertThat(
            pointersOf {
                resolver.resolve(userId, command(categoryName = "Coffee", accountId = UUID.randomUUID()), earlier)
            },
        ).containsExactly("#/date NotFuture", "#/accountId NotFound")
        verify(exactly = 0) { inserts.findOrCreateCategory(any(), any(), any()) }
        verify(exactly = 0) { inserts.findOrCreateAccount(any(), any(), any()) }
    }
}
