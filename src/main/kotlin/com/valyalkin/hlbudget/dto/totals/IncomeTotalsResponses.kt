package com.valyalkin.hlbudget.dto.totals

import com.valyalkin.hlbudget.dto.MoneyTotalDto
import com.valyalkin.hlbudget.dto.RefDto
import com.valyalkin.hlbudget.dto.toDto
import com.valyalkin.hlbudget.dto.toTotalDto
import com.valyalkin.hlbudget.model.AccountTotals
import com.valyalkin.hlbudget.model.CategoryTotals
import com.valyalkin.hlbudget.model.MonthlyTotals
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

// Income sums (feature 009, research R-003). The totals models are shared with spendings; here
// their `spendingCount` is the number of income entries, published as `entryCount`.

@Schema(name = "IncomeMonthTotal", description = "One month's income; zero when nothing was received")
data class IncomeMonthTotalResponse(
    @field:Schema(example = "2026-10", pattern = "^\\d{4}-\\d{2}$")
    val month: String,
    val total: MoneyTotalDto,
    @field:Schema(description = "How many income entries make up the total", example = "3", minimum = "0")
    val entryCount: Long,
)

@Schema(name = "IncomeMonthlyTotals", description = "Income for every month of a year, January to December")
data class IncomeMonthlyTotalsResponse(
    @field:Schema(example = "2026")
    val year: Int,
    @field:Schema(description = "Today in the app's time zone", example = "2026-10-28")
    val today: LocalDate,
    @field:ArraySchema(minItems = 12, maxItems = 12)
    val months: List<IncomeMonthTotalResponse>,
)

@Schema(name = "IncomeCategoryTotal", description = "One income category's income in the range")
data class IncomeCategoryTotalResponse(
    val category: RefDto,
    val total: MoneyTotalDto,
    @field:Schema(example = "1", minimum = "1")
    val entryCount: Long,
    @field:Schema(
        description = "The income category's colour, the same as IncomeCategory.color",
        example = "#1e5b45",
        pattern = "^#[0-9a-f]{6}$",
    )
    val color: String,
)

@Schema(
    name = "IncomeCategoryTotals",
    description = "Income per income category over a date range: only categories with income, largest first",
)
data class IncomeCategoryTotalsResponse(
    @field:Schema(example = "2026-10-01")
    val from: LocalDate,
    @field:Schema(example = "2026-10-31")
    val to: LocalDate,
    val total: MoneyTotalDto,
    @field:Schema(example = "3", minimum = "0")
    val entryCount: Long,
    val items: List<IncomeCategoryTotalResponse>,
)

@Schema(name = "IncomeAccountTotal", description = "One account's income in the range")
data class IncomeAccountTotalResponse(
    val account: RefDto,
    val total: MoneyTotalDto,
    @field:Schema(example = "2", minimum = "1")
    val entryCount: Long,
    @field:Schema(
        description = "The account's colour, the same as Account.color",
        example = "#e11d48",
        pattern = "^#[0-9a-f]{6}$",
    )
    val color: String,
)

@Schema(
    name = "IncomeAccountTotals",
    description = "Income per account over a date range: only accounts that received income, largest first",
)
data class IncomeAccountTotalsResponse(
    @field:Schema(example = "2026-10-01")
    val from: LocalDate,
    @field:Schema(example = "2026-10-31")
    val to: LocalDate,
    val total: MoneyTotalDto,
    @field:Schema(example = "3", minimum = "0")
    val entryCount: Long,
    val items: List<IncomeAccountTotalResponse>,
)

fun MonthlyTotals.toIncomeResponse(): IncomeMonthlyTotalsResponse =
    IncomeMonthlyTotalsResponse(
        year = year,
        today = today,
        months = months.map { IncomeMonthTotalResponse(it.month.toString(), it.total.toTotalDto(), it.spendingCount) },
    )

fun CategoryTotals.toIncomeResponse(): IncomeCategoryTotalsResponse =
    IncomeCategoryTotalsResponse(
        from = from,
        to = to,
        total = total.toTotalDto(),
        entryCount = spendingCount,
        items =
            items.map {
                IncomeCategoryTotalResponse(it.category.toDto(), it.total.toTotalDto(), it.spendingCount, it.color)
            },
    )

fun AccountTotals.toIncomeResponse(): IncomeAccountTotalsResponse =
    IncomeAccountTotalsResponse(
        from = from,
        to = to,
        total = total.toTotalDto(),
        entryCount = spendingCount,
        items =
            items.map {
                IncomeAccountTotalResponse(it.account.toDto(), it.total.toTotalDto(), it.spendingCount, it.color)
            },
    )
