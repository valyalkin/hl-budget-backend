package com.valyalkin.hlbudget.model

import java.util.UUID

/** Where money was paid from. [spendingCount] is derived on every read, never stored (research R-011). */
data class Account(
    val id: UUID,
    val name: String,
    val spendingCount: Long,
)
