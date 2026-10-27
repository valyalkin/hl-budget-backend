package com.valyalkin.hlbudget.dto.spending

import com.valyalkin.hlbudget.dto.MoneyDto
import com.valyalkin.hlbudget.dto.RefDto
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Schema(name = "Spending", description = "One of the caller's spendings")
data class SpendingResponse(
    @field:Schema(example = "8d3a6b1f-4c2e-4f7a-9b0d-1e2c3a4b5c66")
    val id: UUID,
    @field:Schema(example = "2026-10-20")
    val date: LocalDate,
    @field:Schema(example = "Loblaws")
    val merchant: String,
    val amount: MoneyDto,
    val category: RefDto,
    val account: RefDto,
    @field:Schema(example = "2026-10-27T14:03:11Z")
    val createdAt: Instant,
    @field:Schema(example = "2026-10-27T14:03:11Z")
    val updatedAt: Instant,
)
