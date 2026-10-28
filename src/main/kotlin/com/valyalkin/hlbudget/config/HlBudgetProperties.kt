package com.valyalkin.hlbudget.config

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.ZoneId
import java.util.Currency

/**
 * Typed, validated configuration. A missing, blank or invalid value fails startup naming the
 * property: the empty defaults exist only so a missing value reaches @NotBlank instead of a null
 * check. Bound from HLBUDGET_AUTH_ISSUER, HLBUDGET_AUTH_AUDIENCE, HLBUDGET_AUTH_JWKSETURI,
 * HLBUDGET_AUTH_CLAIMSNAMESPACE, HLBUDGET_MONEY_CURRENCY and HLBUDGET_TIMEZONE.
 */
@Validated
@ConfigurationProperties(prefix = "hlbudget")
data class HlBudgetProperties(
    @field:Valid val auth: Auth,
    @field:Valid val money: Money = Money(),
    /** IANA zone that decides what "today" is, e.g. `America/Toronto` (research R-006). */
    @field:NotBlank val timeZone: String = "",
) {
    /** The parsed zone. Safe to read once the context has started: validation rejected bad values. */
    val zoneId: ZoneId get() = ZoneId.of(timeZone)

    @get:AssertTrue(message = "must be a valid IANA time zone, e.g. America/Toronto")
    val isTimeZoneValid: Boolean
        get() = timeZone.isBlank() || runCatching { ZoneId.of(timeZone) }.isSuccess

    data class Auth(
        /** Expected `iss`, with Auth0's trailing slash. */
        @field:NotBlank val issuer: String = "",
        /** Expected `aud`: the HL Budget API identifier. */
        @field:NotBlank val audience: String = "",
        /** Auth0's JWKS endpoint, fetched lazily on the first token. */
        @field:NotBlank val jwkSetUri: String = "",
        /** Prefix of the profile claims the Post-Login Action adds, e.g. `https://hl-budget/`. */
        @field:NotBlank val claimsNamespace: String = "",
    )

    data class Money(
        /** ISO 4217 code of the single app currency, e.g. `CAD`. Every amount is in it. */
        @field:NotBlank val currency: String = "",
    ) {
        /** The parsed currency. Safe to read once the context has started. */
        val parsedCurrency: Currency get() = Currency.getInstance(currency)

        @get:AssertTrue(message = "must be an upper-case ISO 4217 currency code, e.g. CAD")
        val isCurrencyValid: Boolean
            get() = currency.isBlank() || (currency.matches(ISO_CODE) && runCatching { parsedCurrency }.isSuccess)

        private companion object {
            val ISO_CODE = Regex("^[A-Z]{3}$")
        }
    }
}
