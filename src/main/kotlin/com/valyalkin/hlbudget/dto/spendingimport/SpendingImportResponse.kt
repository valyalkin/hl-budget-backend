package com.valyalkin.hlbudget.dto.spendingimport

import com.valyalkin.hlbudget.dto.MoneyTotalDto
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The receipt of a completed import (feature 004). */
@Schema(
    name = "SpendingImport",
    description = "The receipt of a completed import. Holds nothing from the imported file",
)
data class SpendingImportResponse(
    @field:Schema(example = "9b2f6e1a-4c3d-4e5f-8a7b-0c1d2e3f4a5b")
    val id: UUID,
    @field:Schema(description = "How many spendings were created", minimum = "1", maximum = "1000", example = "2")
    val spendingCount: Int,
    @field:Schema(description = "The earliest spending date in the import", example = "2026-09-30")
    val firstDate: LocalDate,
    @field:Schema(
        description = "The latest spending date in the import; clients show this month afterwards",
        example = "2026-10-28",
    )
    val lastDate: LocalDate,
    val total: MoneyTotalDto,
    @field:Schema(example = "2026-10-29T14:03:11Z")
    val createdAt: Instant,
)
