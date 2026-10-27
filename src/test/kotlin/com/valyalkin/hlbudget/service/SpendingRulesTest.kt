package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.config.HlBudgetProperties
import com.valyalkin.hlbudget.model.SpendingCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Research R-006/R-007: the rules only the service can check, and the idempotency fingerprint. */
class SpendingRulesTest {
    // 03:30 UTC on the 28th is still the evening of the 27th in Toronto.
    private val clock = Clock.fixed(Instant.parse("2026-10-28T03:30:00Z"), ZoneOffset.UTC)
    private val properties =
        HlBudgetProperties(
            auth = HlBudgetProperties.Auth("i", "a", "j", "n"),
            money = HlBudgetProperties.Money("CAD"),
            timeZone = "America/Toronto",
        )
    private val rules = SpendingRules(clock, properties)
    private val category = UUID.fromString("2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55")
    private val account = UUID.fromString("3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11")

    private fun command(
        date: String = "2026-10-27",
        amount: String = "84.37",
        currency: String = "CAD",
        merchant: String = "Loblaws",
    ) = SpendingCommand(LocalDate.parse(date), merchant, BigDecimal(amount), currency, category, account)

    @Test
    fun `today in the app's time zone is accepted even when UTC is already tomorrow`() {
        assertThat(rules.violations(command(date = "2026-10-27"))).isEmpty()
        assertThat(rules.today()).isEqualTo(LocalDate.parse("2026-10-27"))
    }

    @Test
    fun `a date after today is NotFuture`() {
        assertThat(rules.violations(command(date = "2026-10-28"))).singleElement().satisfies({
            assertThat(it.pointer).isEqualTo("#/date")
            assertThat(it.code).isEqualTo("NotFuture")
            assertThat(it.message).isEqualTo("must be today or earlier")
        })
    }

    @Test
    fun `zero is not a positive amount`() {
        listOf("0", "0.00").forEach { zero ->
            assertThat(rules.violations(command(amount = zero))).singleElement().satisfies({
                assertThat(it.pointer).isEqualTo("#/amount/amount")
                assertThat(it.field).isEqualTo("amount.amount")
                assertThat(it.code).isEqualTo("Positive")
            })
        }
    }

    @Test
    fun `any currency but the configured one is unsupported`() {
        assertThat(rules.violations(command(currency = "EUR"))).singleElement().satisfies({
            assertThat(it.pointer).isEqualTo("#/amount/currency")
            assertThat(it.code).isEqualTo("UnsupportedCurrency")
            assertThat(it.message).isEqualTo("must be CAD")
        })
        assertThat(rules.currency.currencyCode).isEqualTo("CAD")
    }

    @Test
    fun `every broken rule is reported at once`() {
        assertThat(rules.violations(command(date = "2027-01-01", amount = "0", currency = "EUR")))
            .extracting<String> { it.code }
            .containsExactly("NotFuture", "Positive", "UnsupportedCurrency")
    }

    @Test
    fun `the fingerprint ignores the amount's scale and is stable`() {
        val fingerprint = rules.fingerprint(command(amount = "84.3"))

        assertThat(fingerprint).matches("[0-9a-f]{64}")
        assertThat(rules.fingerprint(command(amount = "84.30"))).isEqualTo(fingerprint)
        assertThat(rules.fingerprint(command(amount = "84.31"))).isNotEqualTo(fingerprint)
        assertThat(rules.fingerprint(command(merchant = "Metro"))).isNotEqualTo(fingerprint)
        assertThat(rules.fingerprint(command(date = "2026-10-26"))).isNotEqualTo(fingerprint)
    }
}
