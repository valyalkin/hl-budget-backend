package com.valyalkin.hlbudget.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One row of an import (feature 004): a spending whose category and account are given by name.
 * [merchant] is trimmed; [amount] has at most 2 decimals.
 */
data class ImportRowCommand(
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val categoryName: String,
    val accountName: String,
)

/** The receipt of a completed import. It holds nothing from the imported file. */
data class SpendingImport(
    val id: UUID,
    val spendingCount: Int,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val total: Money,
    val createdAt: Instant,
)

/** The outcome of an import: the receipt, and whether it was an idempotent replay. */
data class CreateImportResult(
    val import: SpendingImport,
    val replayed: Boolean,
)
