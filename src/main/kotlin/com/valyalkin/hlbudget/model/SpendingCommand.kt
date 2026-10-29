package com.valyalkin.hlbudget.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Create or replace a spending. [merchant] is trimmed; [amount] has at most 2 decimals.
 *
 * The category and the account are each given by id or by name (feature 003, research R-004).
 * The service checks that exactly one of each pair is present and resolves a name to the caller's
 * item, creating it when none matches.
 */
data class SpendingCommand(
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val categoryId: UUID? = null,
    val accountId: UUID? = null,
    val categoryName: String? = null,
    val accountName: String? = null,
)

/** The outcome of a create: the spending, and whether it was an idempotent replay of an earlier one. */
data class CreateResult(
    val spending: Spending,
    val replayed: Boolean,
)
