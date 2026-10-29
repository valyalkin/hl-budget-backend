package com.valyalkin.hlbudget.dto

import com.valyalkin.hlbudget.model.Money
import io.swagger.v3.oas.annotations.media.Schema
import java.math.RoundingMode

/** A sum of amounts on the wire (feature 003). Unlike [MoneyDto] it may be zero and has no digit cap. */
@Schema(
    name = "MoneyTotal",
    description = "A sum of amounts. Unlike Money it may be zero and has no 8-digit cap. Always 2 decimals",
)
data class MoneyTotalDto(
    @field:Schema(example = "2748.42", pattern = "^\\d+\\.\\d{2}$")
    val amount: String,
    @field:Schema(description = "ISO 4217 code: the app's configured currency", example = "CAD", pattern = "^[A-Z]{3}$")
    val currency: String,
)

fun Money.toTotalDto(): MoneyTotalDto =
    MoneyTotalDto(amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(), currency.currencyCode)
