package com.valyalkin.hlbudget.model

import java.time.LocalDate
import java.time.YearMonth

/** One month's spending: its total and how many spendings make it up (feature 003, R-002). */
data class MonthTotal(
    val month: YearMonth,
    val total: Money,
    val spendingCount: Long,
)

/** Every month of [year], January to December, and today's date in the app's time zone. */
data class MonthlyTotals(
    val year: Int,
    val today: LocalDate,
    val months: List<MonthTotal>,
)

/** One category's spending over a date range. Only categories with spending appear. */
data class CategoryTotal(
    val category: Ref,
    val total: Money,
    val spendingCount: Long,
)

/** Spending per category over [from]..[to], largest first, with the range's own total. */
data class CategoryTotals(
    val from: LocalDate,
    val to: LocalDate,
    val total: Money,
    val spendingCount: Long,
    val items: List<CategoryTotal>,
)

/**
 * A merchant derived from the caller's spendings: spendings whose merchant is equal ignoring case
 * and surrounding spaces. [name], [lastCategory] and [lastAccount] come from its most recent
 * spending (FR-057, FR-058).
 */
data class Merchant(
    val name: String,
    val spendingCount: Long,
    val lastUsedOn: LocalDate,
    val lastCategory: Ref,
    val lastAccount: Ref,
)

/** The most used merchants, and whether there were more than were returned. */
data class MerchantList(
    val items: List<Merchant>,
    val truncated: Boolean,
)
