package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.config.HlBudgetProperties
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.spending.CategoryTotalRow
import com.valyalkin.hlbudget.repository.spending.MonthTotalRow
import com.valyalkin.hlbudget.repository.spending.TotalsQueries
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

/** Feature 003, research R-002: totals by month and by category. */
class TotalsServiceTest {
    // 03:00 UTC on 1 November is still 31 October in Toronto.
    private val clock = Clock.fixed(Instant.parse("2026-11-01T03:00:00Z"), ZoneOffset.UTC)
    private val rules =
        SpendingRules(
            clock,
            HlBudgetProperties(
                HlBudgetProperties.Auth("i", "a", "j", "n"),
                HlBudgetProperties.Money("CAD"),
                "America/Toronto",
            ),
        )
    private val currentUser = mockk<CurrentUserService>()
    private val queries = mockk<TotalsQueries>()
    private val service = TotalsService(currentUser, queries, rules)
    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun caller() {
        every { currentUser.resolve(identity) } returns userId
    }

    @Test
    fun `twelve months come back, empty ones as zero, and today is in the app's time zone`() {
        every { queries.monthly(userId, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")) } returns
            listOf(MonthTotalRow(10, BigDecimal("101.36"), 2), MonthTotalRow(3, BigDecimal("5.00"), 1))

        val totals = service.monthly(identity, null)

        assertThat(totals.year).isEqualTo(2026)
        assertThat(totals.today).isEqualTo(LocalDate.parse("2026-10-31"))
        assertThat(totals.months.map { it.month }).containsExactlyElementsOf((1..12).map { YearMonth.of(2026, it) })
        assertThat(totals.months[9].total.amount).isEqualByComparingTo("101.36")
        assertThat(totals.months[9].spendingCount).isEqualTo(2)
        assertThat(totals.months[0].total.amount).isEqualByComparingTo("0")
        assertThat(
            totals.months[0]
                .total.currency.currencyCode,
        ).isEqualTo("CAD")
        assertThat(totals.months[0].spendingCount).isZero()
    }

    @Test
    fun `an explicit year is used as given`() {
        every { queries.monthly(userId, LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31")) } returns
            emptyList()

        assertThat(service.monthly(identity, 2024).year).isEqualTo(2024)
        verify { queries.monthly(userId, LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31")) }
    }

    @Test
    fun `category totals add up to the range total`() {
        val from = LocalDate.parse("2026-10-01")
        val to = LocalDate.parse("2026-10-31")
        every { queries.byCategory(userId, from, to) } returns
            listOf(
                CategoryTotalRow(UUID.randomUUID(), "Rent", BigDecimal("2150.00"), 1),
                CategoryTotalRow(UUID.randomUUID(), "Groceries", BigDecimal("176.44"), 2),
            )

        val totals = service.byCategory(identity, from, to)

        assertThat(totals.items.map { it.category.name }).containsExactly("Rent", "Groceries")
        assertThat(totals.total.amount).isEqualByComparingTo("2326.44")
        assertThat(totals.spendingCount).isEqualTo(3)
    }

    @Test
    fun `nothing spent is a zero total and no items`() {
        val day = LocalDate.parse("2026-02-01")
        every { queries.byCategory(userId, day, day) } returns emptyList()

        val totals = service.byCategory(identity, day, day)

        assertThat(totals.items).isEmpty()
        assertThat(totals.total.amount).isEqualByComparingTo("0")
        assertThat(totals.spendingCount).isZero()
    }

    @Test
    fun `a reversed or too long range is invalid`() {
        assertThatThrownBy {
            service.byCategory(identity, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-10-01"))
        }.isInstanceOfSatisfying(ValidationException::class.java) {
            assertThat(it.violations.single().pointer).isEqualTo("#/from")
        }
        assertThatThrownBy {
            service.byCategory(identity, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-03"))
        }.isInstanceOfSatisfying(ValidationException::class.java) {
            assertThat(it.violations.single().pointer).isEqualTo("#/to")
        }
    }
}
