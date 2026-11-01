package com.valyalkin.hlbudget.dto.income

import com.valyalkin.hlbudget.dto.MoneyDto
import com.valyalkin.hlbudget.model.IncomeCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * The body for creating an income entry, and for replacing all of its details (feature 009). The
 * income category and the account are each given by id or by name (exactly one of each pair).
 */
@Schema(name = "IncomeRequest")
data class IncomeRequest(
    @field:NotNull(message = "is required")
    @field:Schema(description = "Today or earlier, in the app's time zone", example = "2026-10-15")
    val date: LocalDate,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_SOURCE, message = "must be at most 255 characters")
    @field:Schema(description = "Who paid it", example = "Acme Corp", minLength = 1, maxLength = MAX_SOURCE)
    val source: String,
    @field:NotNull(message = "is required")
    @field:Valid
    val amount: MoneyDto,
    @field:Schema(
        description = "One of the caller's income categories. Give exactly one of categoryId and categoryName",
        example = "6e7f8091-2b3c-4d4e-8f5a-6b7c8d9e0f12",
    )
    val categoryId: UUID? = null,
    @field:Schema(
        description = "One of the caller's accounts. Give exactly one of accountId and accountName",
        example = "3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11",
    )
    val accountId: UUID? = null,
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description =
            "Instead of categoryId: matched against the caller's income categories ignoring case and " +
                "surrounding spaces. Refused (422, NotFound) when none matches, including a spending " +
                "category's name; never creates a category",
        example = "Salary",
        maxLength = MAX_NAME,
    )
    val categoryName: String? = null,
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description =
            "Instead of accountId: matched against the caller's accounts ignoring case and surrounding " +
                "spaces. Refused (422, NotFound) when none matches; never creates an account",
        example = "Debit card",
        maxLength = MAX_NAME,
    )
    val accountName: String? = null,
) {
    companion object {
        const val MAX_SOURCE = 255
        const val MAX_NAME = 50
    }
}

/** Only called after validation, so [MoneyDto.amount] is a well-formed decimal. */
fun IncomeRequest.toCommand(): IncomeCommand =
    IncomeCommand(
        date = date,
        merchant = source.trim(),
        amount = BigDecimal(amount.amount),
        currency = amount.currency,
        categoryId = categoryId,
        accountId = accountId,
        categoryName = categoryName,
        accountName = accountName,
    )
