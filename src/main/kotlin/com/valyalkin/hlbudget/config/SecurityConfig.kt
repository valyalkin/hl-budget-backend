package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.ProblemDetailFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets

/**
 * Auth0 as an OAuth2 resource server (research R-005).
 *
 *  - The decoder is built from the JWKS URI, so startup does not depend on Auth0 being reachable.
 *  - `aud` is validated explicitly: Spring's default checks only issuer and timestamps.
 *  - A blank `sub` is an invalid token: the caller's identity is the subject and nothing else.
 *  - 401/403 are written as problem+json from both entry points (the resource server's own and
 *    the exception-handling one), since they happen before the dispatcher.
 */
@Configuration
class SecurityConfig(
    private val properties: HlBudgetProperties,
    private val problems: ProblemDetailFactory,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(*PUBLIC_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
                oauth2.authenticationEntryPoint { request, response, _ -> unauthenticated(request, response) }
                oauth2.accessDeniedHandler { request, response, _ -> accessDenied(request, response) }
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
            .exceptionHandling { handling ->
                handling
                    .authenticationEntryPoint { request, response, _ -> unauthenticated(request, response) }
                    .accessDeniedHandler { request, response, _ -> accessDenied(request, response) }
            }
        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder
            .withJwkSetUri(properties.auth.jwkSetUri)
            .build()
            .apply { setJwtValidator(tokenValidator()) }

    private fun tokenValidator(): OAuth2TokenValidator<Jwt> =
        DelegatingOAuth2TokenValidator(
            JwtValidators.createDefaultWithIssuer(properties.auth.issuer),
            JwtClaimValidator<List<String>>(JwtClaimNames.AUD, ::hasExpectedAudience),
            JwtClaimValidator<String>(JwtClaimNames.SUB, ::hasSubject),
        )

    /** Nullable on purpose: Spring passes null for a missing claim despite the Java signature. */
    private fun hasExpectedAudience(audiences: List<String>?): Boolean =
        audiences?.contains(properties.auth.audience) == true

    private fun hasSubject(subject: String?): Boolean = !subject.isNullOrBlank()

    private fun unauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        writeProblem(request, response, HttpStatus.UNAUTHORIZED, "A valid bearer token is required")
    }

    private fun accessDenied(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) = writeProblem(request, response, HttpStatus.FORBIDDEN, "You are not allowed to do this")

    private fun writeProblem(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        detail: String,
    ) {
        val problem = problems.create(status, detail, request)
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        // Set before taking the writer: a servlet response defaults to ISO-8859-1.
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, problem)
    }

    private companion object {
        val PUBLIC_PATHS =
            arrayOf(
                "/healthz",
                "/readyz",
                "/api/v3/api-docs",
                "/api/v3/api-docs.yaml",
                "/api/v3/api-docs/**",
                "/api/swagger-ui/**",
                "/api/swagger-ui.html",
                "/error",
            )
    }
}
