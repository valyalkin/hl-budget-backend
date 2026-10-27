package com.valyalkin.hlbudget.dto

import com.valyalkin.hlbudget.model.Money
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.math.RoundingMode

/** Money on the wire: an exact decimal string and an ISO 4217 code (Principle VII, research R-006). */
@Schema(name = "Money", description = "An exact amount and its currency")
data class MoneyDto(
    @field:NotNull(message = "is required")
    @field:Pattern(
        regexp = AMOUNT_PATTERN,
        message = "must be a decimal string with up to 8 digits before the point and 2 after",
    )
    @field:Schema(
        description = "Exact decimal as a string, greater than 0. Responses always carry 2 decimals",
        example = "84.37",
        pattern = AMOUNT_PATTERN,
    )
    val amount: String,
    @field:NotNull(message = "is required")
    @field:Pattern(regexp = CURRENCY_PATTERN, message = "must be a 3-letter ISO 4217 code")
    @field:Schema(
        description = "ISO 4217 code. Must be the app's configured currency",
        example = "CAD",
        pattern = CURRENCY_PATTERN,
    )
    val currency: String,
)

private const val AMOUNT_PATTERN = "^\\d{1,8}(\\.\\d{1,2})?$"
private const val CURRENCY_PATTERN = "^[A-Z]{3}$"

fun Money.toDto(): MoneyDto =
    MoneyDto(amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(), currency.currencyCode)
