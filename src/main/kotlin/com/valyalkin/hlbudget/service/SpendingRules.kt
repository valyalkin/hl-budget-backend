package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.config.HlBudgetProperties
import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.model.SpendingCommand
import org.springframework.stereotype.Component
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.HexFormat

/**
 * The spending rules that need the clock or configuration, so Bean Validation cannot check them
 * (research R-006), and the fingerprint that tells a retry from a reused key (research R-007).
 */
@Component
class SpendingRules(
    private val clock: Clock,
    properties: HlBudgetProperties,
) {
    val currency: Currency = properties.money.parsedCurrency
    private val zone = properties.zoneId

    /** "Today" where the user lives, not in UTC: an evening entry must not count as tomorrow. */
    fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    fun violations(command: SpendingCommand): List<FieldViolation> =
        buildList {
            if (command.date.isAfter(today())) {
                add(FieldViolation("#/date", "date", "NotFuture", "must be today or earlier"))
            }
            if (command.amount.signum() <= 0) {
                add(FieldViolation("#/amount/amount", "amount.amount", "Positive", "must be greater than 0"))
            }
            if (command.currency != currency.currencyCode) {
                add(
                    FieldViolation(
                        "#/amount/currency",
                        "amount.currency",
                        "UnsupportedCurrency",
                        "must be ${currency.currencyCode}",
                    ),
                )
            }
        }

    /** SHA-256 of the normalised command, so `"84.3"` and `"84.30"` count as the same request. */
    fun fingerprint(command: SpendingCommand): String {
        val normalised =
            listOf(
                command.date,
                command.merchant.trim(),
                command.amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                command.currency,
                command.categoryId,
                command.accountId,
            ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
