package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.UnauthenticatedException
import com.valyalkin.hlbudget.model.VerifiedIdentity
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.net.URI

/**
 * Builds the caller's [VerifiedIdentity] from the validated token, applying data-model.md's rules,
 * and supplies it to any controller parameter of that type. Nothing about the caller's identity
 * can come from the request itself (FR-015, FR-017).
 */
@Component
class VerifiedIdentityResolver(
    properties: HlBudgetProperties,
) : HandlerMethodArgumentResolver {
    private val log = LoggerFactory.getLogger(javaClass)
    private val namespace = properties.auth.claimsNamespace

    fun resolve(jwt: Jwt): VerifiedIdentity {
        val subject =
            jwt.subject?.takeIf { it.isNotBlank() } ?: throw UnauthenticatedException("The token has no subject")
        return VerifiedIdentity(
            subject = subject,
            displayName = text("name", jwt)?.take(MAX_NAME),
            email = email(jwt),
            avatarUrl = text("picture", jwt)?.takeIf(::isHttpsUrl),
        )
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == VerifiedIdentity::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): VerifiedIdentity {
        val authentication =
            SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
                ?: throw UnauthenticatedException("A valid bearer token is required")
        return resolve(authentication.token)
    }

    private fun text(
        claim: String,
        jwt: Jwt,
    ): String? = (jwt.claims[namespace + claim] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun email(jwt: Jwt): String? {
        val value = text("email", jwt) ?: return null
        if (!value.contains('@') || value.length > MAX_EMAIL) {
            // The value is personal data and stays out of the log.
            log.warn("Ignoring an invalid email claim for the caller")
            return null
        }
        return value
    }

    private fun isHttpsUrl(value: String): Boolean {
        if (value.length > MAX_URL) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    private companion object {
        const val MAX_NAME = 255
        const val MAX_EMAIL = 320
        const val MAX_URL = 2048
    }
}
