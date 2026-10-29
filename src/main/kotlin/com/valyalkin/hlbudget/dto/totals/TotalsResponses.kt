package com.valyalkin.hlbudget.dto.totals

import com.valyalkin.hlbudget.dto.MoneyTotalDto
import com.valyalkin.hlbudget.dto.RefDto
import com.valyalkin.hlbudget.dto.toDto
import com.valyalkin.hlbudget.dto.toTotalDto
import com.valyalkin.hlbudget.model.CategoryTotals
import com.valyalkin.hlbudget.model.MonthlyTotals
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(name = "MonthTotal", description = "One month's total; zero when nothing was spent")
data class MonthTotalResponse(
    @field:Schema(example = "2026-10", pattern = "^\\d{4}-\\d{2}$")
    val month: String,
    val total: MoneyTotalDto,
    @field:Schema(example = "14", minimum = "0")
    val spendingCount: Long,
)

@Schema(name = "MonthlyTotals", description = "Every month of a year, January to December")
data class MonthlyTotalsResponse(
    @field:Schema(example = "2026")
    val year: Int,
    @field:Schema(description = "Today in the app's time zone", example = "2026-10-28")
    val today: LocalDate,
    @field:ArraySchema(minItems = 12, maxItems = 12)
    val months: List<MonthTotalResponse>,
)

@Schema(name = "CategoryTotal", description = "One category's spending in the range")
data class CategoryTotalResponse(
    val category: RefDto,
    val total: MoneyTotalDto,
    @field:Schema(example = "2", minimum = "1")
    val spendingCount: Long,
)

@Schema(
    name = "CategoryTotals",
    description = "Spending per category over a date range: only categories with spending, largest first",
)
data class CategoryTotalsResponse(
    @field:Schema(example = "2026-10-01")
    val from: LocalDate,
    @field:Schema(example = "2026-10-31")
    val to: LocalDate,
    val total: MoneyTotalDto,
    @field:Schema(example = "14", minimum = "0")
    val spendingCount: Long,
    val items: List<CategoryTotalResponse>,
)

fun MonthlyTotals.toResponse(): MonthlyTotalsResponse =
    MonthlyTotalsResponse(
        year = year,
        today = today,
        months = months.map { MonthTotalResponse(it.month.toString(), it.total.toTotalDto(), it.spendingCount) },
    )

fun CategoryTotals.toResponse(): CategoryTotalsResponse =
    CategoryTotalsResponse(
        from = from,
        to = to,
        total = total.toTotalDto(),
        spendingCount = spendingCount,
        items = items.map { CategoryTotalResponse(it.category.toDto(), it.total.toTotalDto(), it.spendingCount) },
    )
