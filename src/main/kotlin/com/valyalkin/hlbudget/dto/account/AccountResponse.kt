package com.valyalkin.hlbudget.dto.account

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(name = "Account", description = "Where the caller's money was paid from")
data class AccountResponse(
    @field:Schema(example = "3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11")
    val id: UUID,
    @field:Schema(example = "Amex", maxLength = 50)
    val name: String,
    @field:Schema(
        description = "How many of the caller's spendings use this account",
        accessMode = Schema.AccessMode.READ_ONLY,
        minimum = "0",
    )
    val spendingCount: Long,
)

@Schema(name = "AccountList", description = "All of the caller's accounts, sorted by name (never paged)")
data class AccountListResponse(
    val items: List<AccountResponse>,
)
