package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.config.HlBudgetProperties
import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.SpendingCommand
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.util.Currency
import java.util.HexFormat
import java.util.Locale
import java.util.UUID

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
        violations(command.date, command.amount, command.currency, pointer = "#", field = "")

    /** The same rules for row [index] of an import, pointing into that row (feature 004, R-007). */
    fun violations(
        row: ImportRowCommand,
        index: Int,
    ): List<FieldViolation> =
        violations(row.date, row.amount, row.currency, pointer = "#/spendings/$index", field = "spendings[$index].")

    private fun violations(
        date: LocalDate,
        amount: BigDecimal,
        currency: String,
        pointer: String,
        field: String,
    ): List<FieldViolation> =
        buildList {
            if (date.isAfter(today())) {
                add(FieldViolation("$pointer/date", "${field}date", "NotFuture", "must be today or earlier"))
            }
            if (amount.signum() <= 0) {
                add(
                    FieldViolation(
                        "$pointer/amount/amount",
                        "${field}amount.amount",
                        "Positive",
                        "must be greater than 0",
                    ),
                )
            }
            if (currency != this@SpendingRules.currency.currencyCode) {
                add(
                    FieldViolation(
                        "$pointer/amount/currency",
                        "${field}amount.currency",
                        "UnsupportedCurrency",
                        "must be ${this@SpendingRules.currency.currencyCode}",
                    ),
                )
            }
        }

    /**
     * SHA-256 of the normalised command, so `"84.3"` and `"84.30"` count as the same request. The
     * category and account are the resolved ids, whether the request named them or gave ids.
     */
    fun fingerprint(
        command: SpendingCommand,
        categoryId: UUID,
        accountId: UUID,
    ): String {
        val normalised =
            listOf(
                command.date,
                command.merchant.trim(),
                command.amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                command.currency,
                categoryId,
                accountId,
            ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    /**
     * SHA-256 over an import's rows in order (feature 004, data-model.md "Fingerprint"). Names are
     * compared ignoring case and surrounding spaces, as they are resolved, and `"84.3"` equals
     * `"84.30"`, so a retry of the same rows always matches.
     */
    fun importFingerprint(rows: List<ImportRowCommand>): String {
        val normalised =
            rows.joinToString("\n") { row ->
                listOf(
                    row.date,
                    row.merchant.trim(),
                    row.amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    row.currency,
                    row.categoryName.trim().lowercase(Locale.ROOT),
                    row.accountName.trim().lowercase(Locale.ROOT),
                ).joinToString("|")
            }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
