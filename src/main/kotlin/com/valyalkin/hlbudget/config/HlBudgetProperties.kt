package com.valyalkin.hlbudget.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Typed, validated configuration. A missing or blank value fails startup naming the property.
 * Bound from HLBUDGET_AUTH_ISSUER, HLBUDGET_AUTH_AUDIENCE, HLBUDGET_AUTH_JWKSETURI and
 * HLBUDGET_AUTH_CLAIMSNAMESPACE.
 */
@Validated
@ConfigurationProperties(prefix = "hlbudget")
data class HlBudgetProperties(
    @field:Valid val auth: Auth,
) {
    data class Auth(
        /** Expected `iss`, with Auth0's trailing slash. */
        @field:NotBlank val issuer: String,
        /** Expected `aud`: the HL Budget API identifier. */
        @field:NotBlank val audience: String,
        /** Auth0's JWKS endpoint, fetched lazily on the first token. */
        @field:NotBlank val jwkSetUri: String,
        /** Prefix of the profile claims the Post-Login Action adds, e.g. `https://hl-budget/`. */
        @field:NotBlank val claimsNamespace: String,
    )
}
