package com.valyalkin.hlbudget.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One amount of money received, with the current names of its income category and account. */
data class Income(
    val id: UUID,
    val date: LocalDate,
    val source: String,
    val amount: Money,
    val category: Ref,
    val account: Ref,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Create or replace an income entry. The same shape and rules as a spending's command (feature 009,
 * research R-004), so the date, amount and name rules and the fingerprint are shared:
 * `merchant` holds the income's source.
 */
typealias IncomeCommand = SpendingCommand

/** One row of an income import; `merchant` holds the source, as in [IncomeCommand]. */
typealias IncomeImportRowCommand = ImportRowCommand

/** What the income list should return; the same filters and sorts as the spending list. */
typealias IncomeFilter = SpendingFilter

/** One page of the income list, with totals across all pages. */
data class IncomePage(
    val items: List<Income>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)

/** The outcome of a create: the income, and whether it was an idempotent replay. */
data class CreateIncomeResult(
    val income: Income,
    val replayed: Boolean,
)

/** The receipt of a completed income import. It holds nothing from the imported file. */
data class IncomeImport(
    val id: UUID,
    val incomeCount: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val total: Money,
    val createdAt: Instant,
)

/** The outcome of an income import: the receipt, and whether it was an idempotent replay. */
data class CreateIncomeImportResult(
    val import: IncomeImport,
    val replayed: Boolean,
)
