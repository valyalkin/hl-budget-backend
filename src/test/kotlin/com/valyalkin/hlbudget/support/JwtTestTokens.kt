package com.valyalkin.hlbudget.support

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date

/**
 * Real RS256-signed tokens plus the JWKS that WireMock serves, so tests exercise the production
 * decoder and validators rather than a mock.
 */
object JwtTestTokens {
    const val ISSUER: String = "https://test-issuer.example.com/"
    const val AUDIENCE: String = "https://api.hl-budget.test"
    const val NAMESPACE: String = "https://hl-budget/"
    const val JWKS_PATH: String = "/.well-known/jwks.json"
    const val SUBJECT: String = "auth0|test-user"

    private val key: RSAKey = newKey("test-key-1")
    private val unknownKey: RSAKey = newKey("unknown-key")

    val jwksJson: String get() = JWKSet(key.toPublicJWK()).toString()

    /** Defaults to a fully valid token with all three profile claims. */
    @Suppress("LongParameterList")
    fun token(
        subject: String? = SUBJECT,
        audience: List<String>? = listOf(AUDIENCE),
        issuer: String = ISSUER,
        expiresAt: Instant = Instant.now().plusSeconds(3600),
        profile: Map<String, Any?> =
            mapOf(
                "name" to "Alex Example",
                "email" to "alex@example.com",
                "picture" to "https://lh3.googleusercontent.com/a/example",
            ),
        signingKey: RSAKey = key,
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .issuer(issuer)
                .issueTime(Date.from(expiresAt.minusSeconds(7200)))
                .expirationTime(Date.from(expiresAt))
                .apply {
                    subject?.let { subject(it) }
                    audience?.let { audience(it) }
                    profile.forEach { (name, value) -> claim(NAMESPACE + name, value) }
                }.build()
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.keyID)
                .type(JOSEObjectType.JWT)
                .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(signingKey)) }.serialize()
    }

    fun expired(): String = token(expiresAt = Instant.now().minusSeconds(3600))

    fun wrongIssuer(): String = token(issuer = "https://evil.example.com/")

    fun wrongAudience(): String = token(audience = listOf("https://api.someone-else.example.com"))

    fun missingAudience(): String = token(audience = null)

    fun missingSubject(): String = token(subject = null)

    fun blankSubject(): String = token(subject = "  ")

    fun signedByUnknownKey(): String = token(signingKey = unknownKey)

    const val MALFORMED: String = "not-a-jwt"

    private fun newKey(id: String): RSAKey =
        RSAKeyGenerator(RSAKeyGenerator.MIN_KEY_SIZE_BITS)
            .keyID(id)
            .keyUse(KeyUse.SIGNATURE)
            .generate()
}
