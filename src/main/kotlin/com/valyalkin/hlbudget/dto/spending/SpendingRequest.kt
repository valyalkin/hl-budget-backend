package com.valyalkin.hlbudget.dto.spending

import com.valyalkin.hlbudget.dto.MoneyDto
import com.valyalkin.hlbudget.model.SpendingCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** The body for creating a spending, and for replacing all of its details. */
@Schema(name = "SpendingRequest")
data class SpendingRequest(
    @field:NotNull(message = "is required")
    @field:Schema(description = "Today or earlier, in the app's time zone", example = "2026-10-20")
    val date: LocalDate,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_MERCHANT, message = "must be at most 255 characters")
    @field:Schema(example = "Loblaws", minLength = 1, maxLength = MAX_MERCHANT)
    val merchant: String,
    @field:NotNull(message = "is required")
    @field:Valid
    val amount: MoneyDto,
    @field:NotNull(message = "is required")
    @field:Schema(description = "One of the caller's categories", example = "2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55")
    val categoryId: UUID,
    @field:NotNull(message = "is required")
    @field:Schema(description = "One of the caller's accounts", example = "3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11")
    val accountId: UUID,
) {
    companion object {
        const val MAX_MERCHANT = 255
    }
}

/** Only called after validation, so [MoneyDto.amount] is a well-formed decimal. */
fun SpendingRequest.toCommand(): SpendingCommand =
    SpendingCommand(date, merchant.trim(), BigDecimal(amount.amount), amount.currency, categoryId, accountId)
