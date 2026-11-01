package com.valyalkin.hlbudget.dto.income

import com.valyalkin.hlbudget.dto.MoneyDto
import com.valyalkin.hlbudget.dto.RefDto
import com.valyalkin.hlbudget.dto.toDto
import com.valyalkin.hlbudget.model.Income
import com.valyalkin.hlbudget.model.IncomePage
import com.valyalkin.hlbudget.model.MerchantList
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Schema(name = "Income", description = "One of the caller's income entries")
data class IncomeResponse(
    @field:Schema(example = "9b1e2c3d-4f5a-4b6c-8d7e-0f1a2b3c4d5e")
    val id: UUID,
    @field:Schema(example = "2026-10-15")
    val date: LocalDate,
    @field:Schema(description = "Who paid it", example = "Acme Corp")
    val source: String,
    val amount: MoneyDto,
    @field:Schema(description = "One of the caller's income categories")
    val category: RefDto,
    val account: RefDto,
    @field:Schema(example = "2026-10-15T14:03:11Z")
    val createdAt: Instant,
    @field:Schema(example = "2026-10-15T14:03:11Z")
    val updatedAt: Instant,
)

@Schema(
    name = "IncomePage",
    description = "One page of the caller's income entries; `items` is empty when nothing matches",
)
data class IncomePageResponse(
    val items: List<IncomeResponse>,
    @field:Schema(description = "0-based page number", minimum = "0")
    val page: Int,
    @field:Schema(minimum = "1", maximum = "100")
    val size: Int,
    @field:Schema(description = "Income entries matching the filters, across all pages")
    val totalItems: Long,
    val totalPages: Int,
)

@Schema(
    name = "IncomeSource",
    description =
        "A source derived from the caller's income entries: entries whose source is equal ignoring case " +
            "and surrounding spaces. `name`, `lastCategory` and `lastAccount` come from the most recent one",
)
data class IncomeSourceResponse(
    @field:Schema(example = "Acme Corp")
    val name: String,
    @field:Schema(description = "How many of the caller's income entries have this source", minimum = "1")
    val incomeCount: Long,
    @field:Schema(example = "2026-10-15")
    val lastUsedOn: LocalDate,
    val lastCategory: RefDto,
    val lastAccount: RefDto,
)

@Schema(name = "IncomeSourceList", description = "Sources, most used first; `truncated` is true when more exist")
data class IncomeSourceListResponse(
    val items: List<IncomeSourceResponse>,
    val truncated: Boolean,
)

fun Income.toResponse(): IncomeResponse =
    IncomeResponse(id, date, source, amount.toDto(), category.toDto(), account.toDto(), createdAt, updatedAt)

fun IncomePage.toResponse(): IncomePageResponse =
    IncomePageResponse(items.map { it.toResponse() }, page, size, totalItems, totalPages)

/** Sources come back as [MerchantList] over income (MerchantService with Ledger.INCOMES). */
fun MerchantList.toIncomeSourceResponse(): IncomeSourceListResponse =
    IncomeSourceListResponse(
        items.map {
            IncomeSourceResponse(
                name = it.name,
                incomeCount = it.spendingCount,
                lastUsedOn = it.lastUsedOn,
                lastCategory = it.lastCategory.toDto(),
                lastAccount = it.lastAccount.toDto(),
            )
        },
        truncated,
    )
