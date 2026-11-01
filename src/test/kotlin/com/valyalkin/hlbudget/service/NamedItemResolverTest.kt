package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.repository.account.AccountEntity
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryEntity
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Feature 003, research R-004, and feature 007, R-008: a spending's category and account by id or by name. */
class NamedItemResolverTest {
    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val categories = mockk<CategoryRepository>()
    private val accounts = mockk<AccountRepository>()
    private val resolver = NamedItemResolver(categories, accounts)

    private val userId = UUID.randomUUID()
    private val groceries =
        CategoryEntity(UUID.randomUUID(), userId, "Groceries", true, "#c2410c", now, now, 0)
    private val amex = AccountEntity(UUID.randomUUID(), userId, "Amex", now, now, "#e11d48", 0)

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
    }

    @Test
    fun `a name is trimmed and matched against the caller's items`() {
        every { categories.findByUserIdAndNameIgnoreCase(userId, "groceries") } returns groceries
        every { accounts.findByUserIdAndNameIgnoreCase(userId, "AMEX") } returns amex

        val (category, account) =
            resolver.resolve(userId, command(categoryName = " groceries ", accountName = "AMEX"), emptyList())

        assertThat(category).isEqualTo(Ref(groceries.id, "Groceries"))
        assertThat(account).isEqualTo(Ref(amex.id, "Amex"))
    }

    @Test
    fun `an unknown name is NotFound at the name, for each`() {
        every { categories.findByUserIdAndNameIgnoreCase(userId, any()) } returns null
        every { accounts.findByUserIdAndNameIgnoreCase(userId, any()) } returns null

        var messages = emptyList<String>()
        assertThatThrownBy {
            resolver.resolve(userId, command(categoryName = "Coffee", accountName = "Visa"), emptyList())
        }.isInstanceOfSatisfying(ValidationException::class.java) { exception ->
            messages = exception.violations.map { "${it.pointer} ${it.code} ${it.message}" }
        }
        assertThat(messages).containsExactly(
            "#/categoryName NotFound is not one of your categories",
            "#/accountName NotFound is not one of your accounts",
        )
    }

    @Test
    fun `an import row's unknown names are reported at the row's pointers`() {
        every { categories.findAllByUserId(userId) } returns listOf(groceries)
        every { accounts.findAllByUserId(userId) } returns listOf(amex)
        val maps = resolver.names(userId)

        fun row(
            category: String,
            account: String,
        ) = ImportRowCommand(LocalDate.parse("2026-10-20"), "M", BigDecimal.ONE, "CAD", category, account)

        assertThat(maps.violations(row(" GROCERIES", "amex "), 0)).isEmpty()
        assertThat(maps.violations(row("Coffee", "Visa"), 3).map { it.pointer }).containsExactly(
            "#/spendings/3/categoryName",
            "#/spendings/3/accountName",
        )
        assertThat(maps.category("groceries")).isEqualTo(Ref(groceries.id, "Groceries"))
        assertThat(maps.account("AMEX")).isEqualTo(Ref(amex.id, "Amex"))
    }

    @Test
    fun `exactly one of id and name is required for each`() {
        assertThat(pointersOf { resolver.resolve(userId, command(), emptyList()) })
            .containsExactly("#/categoryId ExactlyOne", "#/accountId ExactlyOne")
        every { categories.findByIdAndUserId(groceries.id, userId) } returns groceries
        every { accounts.findByUserIdAndNameIgnoreCase(userId, "Visa") } returns amex
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
    fun `every problem is reported together with earlier ones`() {
        every { accounts.findByIdAndUserId(any(), userId) } returns null
        every { categories.findByUserIdAndNameIgnoreCase(userId, "Coffee") } returns null
        val earlier = listOf(FieldViolation("#/date", "date", "NotFuture", "must be today or earlier"))

        assertThat(
            pointersOf {
                resolver.resolve(userId, command(categoryName = "Coffee", accountId = UUID.randomUUID()), earlier)
            },
        ).containsExactly("#/date NotFuture", "#/categoryName NotFound", "#/accountId NotFound")
    }
}
