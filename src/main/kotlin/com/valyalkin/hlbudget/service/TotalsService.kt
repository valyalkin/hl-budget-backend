package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.CategoryTotal
import com.valyalkin.hlbudget.model.CategoryTotals
import com.valyalkin.hlbudget.model.Money
import com.valyalkin.hlbudget.model.MonthTotal
import com.valyalkin.hlbudget.model.MonthlyTotals
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.spending.TotalsQueries
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Sums of the caller's spendings, for the month line and the By category card (feature 003,
 * research R-002). Sums are exact; nothing here is a floating-point number.
 *
 * Read-write transactions because resolving the caller may create their user row on a first call.
 */
@Service
@Transactional
class TotalsService(
    private val currentUser: CurrentUserService,
    private val queries: TotalsQueries,
    private val rules: SpendingRules,
) {
    /** Twelve months of [year] (the current year in the app's time zone when null), zeros included. */
    fun monthly(
        identity: VerifiedIdentity,
        year: Int?,
    ): MonthlyTotals {
        val userId = currentUser.resolve(identity)
        val today = rules.today()
        val shown = Year.of(year ?: today.year)
        val rows =
            queries
                .monthly(
                    userId,
                    shown.atDay(1),
                    shown.atMonth(DECEMBER).atEndOfMonth(),
                ).associateBy { it.month }
        val months =
            (1..DECEMBER).map { month ->
                val row = rows[month]
                MonthTotal(
                    YearMonth.of(shown.value, month),
                    money(row?.total ?: BigDecimal.ZERO),
                    row?.spendingCount ?: 0,
                )
            }
        return MonthlyTotals(shown.value, today, months)
    }

    /** Spending per category over [from]..[to]; the range must be ordered and at most 366 days. */
    fun byCategory(
        identity: VerifiedIdentity,
        from: LocalDate,
        to: LocalDate,
    ): CategoryTotals {
        checkRange(from, to)
        val userId = currentUser.resolve(identity)
        val items =
            queries.byCategory(userId, from, to).map {
                CategoryTotal(Ref(it.categoryId, it.categoryName), money(it.total), it.spendingCount)
            }
        return CategoryTotals(
            from = from,
            to = to,
            total = money(items.fold(BigDecimal.ZERO) { sum, item -> sum + item.total.amount }),
            spendingCount = items.sumOf { it.spendingCount },
            items = items,
        )
    }

    private fun checkRange(
        from: LocalDate,
        to: LocalDate,
    ) {
        val violation =
            when {
                from.isAfter(to) -> {
                    FieldViolation("#/from", "from", "Range", "must not be after to")
                }

                ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS -> {
                    FieldViolation("#/to", "to", "Range", "must be at most 366 days after from")
                }

                else -> {
                    null
                }
            }
        if (violation != null) throw ValidationException(listOf(violation))
    }

    private fun money(amount: BigDecimal) = Money(amount, rules.currency)

    private companion object {
        const val DECEMBER = 12
        const val MAX_RANGE_DAYS = 366L
    }
}
