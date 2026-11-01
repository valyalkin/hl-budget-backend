package com.valyalkin.hlbudget.model

import java.util.UUID

/**
 * Where money was paid from, or paid into (feature 009). [spendingCount] and [incomeCount] are
 * derived on every read, never stored (research R-011).
 */
data class Account(
    val id: UUID,
    val name: String,
    val spendingCount: Long,
    /** `#rrggbb`, assigned on create and kept for the account's lifetime (feature 008). */
    val color: String,
    /** How many of the owner's income entries use this account (feature 009, FR-053). */
    val incomeCount: Long = 0,
)
