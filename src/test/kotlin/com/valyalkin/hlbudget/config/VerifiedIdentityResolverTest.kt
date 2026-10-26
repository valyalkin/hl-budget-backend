package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.UnauthenticatedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class VerifiedIdentityResolverTest {
    private val namespace = "https://hl-budget/"
    private val resolver =
        VerifiedIdentityResolver(
            HlBudgetProperties(HlBudgetProperties.Auth("https://iss/", "aud", "https://iss/jwks", namespace)),
        )

    private fun jwt(
        subject: String? = "auth0|abc",
        claims: Map<String, Any> = emptyMap(),
    ): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .apply {
                subject?.let { subject(it) }
                claim("iss", "https://iss/")
                claims.forEach { (name, value) -> claim(name, value) }
            }.build()

    private fun profile(
        name: Any? = null,
        email: Any? = null,
        picture: Any? = null,
    ) = buildMap<String, Any> {
        name?.let { put(namespace + "name", it) }
        email?.let { put(namespace + "email", it) }
        picture?.let { put(namespace + "picture", it) }
    }

    @Test
    fun `a full token becomes a full identity`() {
        val identity =
            resolver.resolve(
                jwt(claims = profile(" Alex Example ", " alex@example.com ", "https://cdn.example.com/a.png")),
            )

        assertThat(identity.subject).isEqualTo("auth0|abc")
        assertThat(identity.displayName).isEqualTo("Alex Example")
        assertThat(identity.email).isEqualTo("alex@example.com")
        assertThat(identity.avatarUrl).isEqualTo("https://cdn.example.com/a.png")
    }

    @Test
    fun `a missing or blank subject is an invalid token`() {
        assertThatThrownBy { resolver.resolve(jwt(subject = null)) }.isInstanceOf(UnauthenticatedException::class.java)
        assertThatThrownBy { resolver.resolve(jwt(subject = "   ")) }.isInstanceOf(UnauthenticatedException::class.java)
    }

    @Test
    fun `missing profile claims become nulls`() {
        val identity = resolver.resolve(jwt())

        assertThat(identity.displayName).isNull()
        assertThat(identity.email).isNull()
        assertThat(identity.avatarUrl).isNull()
    }

    @Test
    fun `a blank name is null and a long one is truncated to 255`() {
        assertThat(resolver.resolve(jwt(claims = profile(name = "   "))).displayName).isNull()
        assertThat(resolver.resolve(jwt(claims = profile(name = "x".repeat(300)))).displayName).hasSize(255)
    }

    @ParameterizedTest
    @ValueSource(strings = ["   ", "not-an-email", "a@b.c "])
    fun `email rules`(raw: String) {
        val expected = if (raw.trim().contains('@')) raw.trim() else null
        assertThat(resolver.resolve(jwt(claims = profile(email = raw))).email).isEqualTo(expected)
    }

    @Test
    fun `an email longer than 320 is null`() {
        val long = "a".repeat(315) + "@b.com"
        assertThat(resolver.resolve(jwt(claims = profile(email = long))).email).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://cdn.example.com/a.png",
            "javascript:alert(1)",
            "/relative/a.png",
            "https://",
            "not a url at all",
        ],
    )
    fun `a picture that is not an absolute https URL is null`(raw: String) {
        assertThat(resolver.resolve(jwt(claims = profile(picture = raw))).avatarUrl).isNull()
    }

    @Test
    fun `a picture longer than 2048 is null`() {
        val long = "https://cdn.example.com/" + "a".repeat(2048)
        assertThat(resolver.resolve(jwt(claims = profile(picture = long))).avatarUrl).isNull()
    }

    @Test
    fun `claims are read under the configured namespace only`() {
        val identity =
            resolver.resolve(
                jwt(
                    claims =
                        mapOf(
                            "name" to "Plain",
                            "email" to "plain@example.com",
                            "picture" to "https://x.example/p",
                        ),
                ),
            )

        assertThat(identity.displayName).isNull()
        assertThat(identity.email).isNull()
        assertThat(identity.avatarUrl).isNull()
    }

    @Test
    fun `a non-string claim is ignored`() {
        assertThat(resolver.resolve(jwt(claims = profile(name = 42))).displayName).isNull()
    }
}
