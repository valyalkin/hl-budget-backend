package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.ProblemCodes
import com.valyalkin.hlbudget.error.ProblemDetailFactory
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The OpenAPI document at /api/v3/api-docs. OpenApiExportIT writes it to contracts/api.yaml,
 * which the frontend generates its types from; contracts/openapi.yaml in the spec is the target.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("HL Budget API")
                    .version("1.0.0")
                    .description(DESCRIPTION),
            )
            // Relative, so the exported contract does not change with the test's random port.
            .servers(listOf(Server().url("/").description("Same host as the web app; the backend serves /api")))
            .tags(
                listOf(
                    Tag().name(TAG_USERS).description("The signed-in user's own record"),
                    Tag()
                        .name(TAG_OPERATIONS)
                        .description("Unversioned probes for the platform (not routed through the ingress)"),
                ),
            ).components(OpenApiComponents.components())
            .paths(OpenApiProbes.probePaths())
            .addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))

    companion object {
        const val BEARER_SCHEME = "bearerAuth"
        const val TAG_USERS = "Users"
        const val TAG_OPERATIONS = "Operations"
        const val PROBLEM_SCHEMA = "Problem"
        const val FIELD_VIOLATION_SCHEMA = "FieldViolation"
        const val HEALTH_SCHEMA = "HealthStatus"
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_HEADER_REF = "#/components/headers/X-Request-Id"

        internal val PROBLEM_TYPES =
            listOf(
                ProblemCodes.UNAUTHENTICATED,
                ProblemCodes.ACCESS_DENIED,
                ProblemCodes.BAD_REQUEST,
                ProblemCodes.NOT_FOUND,
                ProblemCodes.CONFLICT,
                ProblemCodes.UNPROCESSABLE_ENTITY,
                ProblemCodes.INTERNAL_ERROR,
                ProblemCodes.SERVICE_UNAVAILABLE,
            ).map { ProblemDetailFactory.TYPE_PREFIX + it }

        private val DESCRIPTION =
            """
            Backend for HL Budget. Every business endpoint is versioned under `/api/v{N}` and requires an
            Auth0 access token for the HL Budget API audience. Every error is RFC 9457
            `application/problem+json` with a stable `type` URN and a `traceId`.
            """.trimIndent()
    }
}
