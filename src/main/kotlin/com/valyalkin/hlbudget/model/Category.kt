package com.valyalkin.hlbudget.model

import java.util.UUID

/** What money was spent on. [spendingCount] is derived on every read, never stored (research R-011). */
data class Category(
    val id: UUID,
    val name: String,
    val isDefault: Boolean,
    val spendingCount: Long,
    /** `#rrggbb`, fixed for the defaults and assigned once to a custom category (feature 007). */
    val color: String,
)
