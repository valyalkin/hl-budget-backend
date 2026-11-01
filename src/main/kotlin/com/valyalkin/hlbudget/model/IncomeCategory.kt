package com.valyalkin.hlbudget.model

import java.util.UUID

/**
 * What kind of income it is (feature 009). A separate list from spending categories, so the same
 * name may exist in both. [incomeCount] is derived on every read, never stored.
 */
data class IncomeCategory(
    val id: UUID,
    val name: String,
    val isDefault: Boolean,
    val incomeCount: Long,
    /** `#rrggbb`, fixed for Salary and Investments and assigned once to a custom one. */
    val color: String,
)
