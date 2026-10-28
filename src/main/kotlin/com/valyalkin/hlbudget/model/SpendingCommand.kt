package com.valyalkin.hlbudget.model

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Create or replace a spending. [merchant] is trimmed; [amount] has at most 2 decimals. */
data class SpendingCommand(
    val date: LocalDate,
    val merchant: String,
    val amount: BigDecimal,
    val currency: String,
    val categoryId: UUID,
    val accountId: UUID,
)

/** The outcome of a create: the spending, and whether it was an idempotent replay of an earlier one. */
data class CreateResult(
    val spending: Spending,
    val replayed: Boolean,
)
