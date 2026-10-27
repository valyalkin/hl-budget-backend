package com.valyalkin.hlbudget.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.UUID

/** One spending transaction, with the current names of its category and account (FR-034). */
data class Spending(
    val id: UUID,
    val date: LocalDate,
    val merchant: String,
    val amount: Money,
    val category: Ref,
    val account: Ref,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** An exact amount in an explicit currency. Never a floating-point number (constitution: Money). */
data class Money(
    val amount: BigDecimal,
    val currency: Currency,
)

/** A reference to another resource, with its name so a reader needs no extra lookup. */
data class Ref(
    val id: UUID,
    val name: String,
)
