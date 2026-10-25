package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.ProblemCodes
import com.valyalkin.hlbudget.error.ProblemDetailFactory
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
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
                        .name(
                            TAG_OPERATIONS,
                        ).description("Unversioned probes for the platform (not routed through the ingress)"),
                ),
            ).components(
                Components()
                    .addSecuritySchemes(
                        BEARER_SCHEME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description(
                                "Auth0 access token (RS256) for the HL Budget API audience. Validated for " +
                                    "signature, issuer, audience and expiry on every request.",
                            ),
                    ).addSchemas(PROBLEM_SCHEMA, problemSchema())
                    .addSchemas(FIELD_VIOLATION_SCHEMA, fieldViolationSchema()),
            ).addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))

    private fun problemSchema(): Schema<Any> =
        ObjectSchema()
            .description("RFC 9457 problem details, as produced by the backend's single error handler")
            .addProperty(
                "type",
                StringSchema()
                    .description("Stable URN naming the error category")
                    .apply { enum = PROBLEM_TYPES },
            ).addProperty("title", StringSchema())
            .addProperty(
                "status",
                IntegerSchema().minimum(MIN_STATUS.toBigDecimal()).maximum(MAX_STATUS.toBigDecimal()),
            ).addProperty(
                "detail",
                StringSchema().description("Human-readable explanation. For 5xx always a fixed, safe sentence"),
            ).addProperty("instance", StringSchema().description("Request path"))
            .addProperty("timestamp", StringSchema().format("date-time"))
            .addProperty("traceId", StringSchema())
            .addProperty(
                "details",
                ObjectSchema()
                    .description("Structured, scalar-only context; `reason` when a client must branch")
                    .additionalProperties(true),
            ).addProperty(
                "errors",
                ArraySchema()
                    .description("One entry per offending field (validation failures only)")
                    .items(Schema<Any>().`$ref`("#/components/schemas/$FIELD_VIOLATION_SCHEMA")),
            ).apply { required = listOf("type", "title", "status", "instance", "timestamp", "traceId") }

    private fun fieldViolationSchema(): Schema<Any> =
        ObjectSchema()
            .addProperty("pointer", StringSchema().example("#/name"))
            .addProperty("field", StringSchema().example("name"))
            .addProperty("code", StringSchema().example("NotBlank"))
            .addProperty("message", StringSchema().example("must not be blank"))
            .apply { required = listOf("pointer", "field", "code", "message") }

    companion object {
        const val BEARER_SCHEME = "bearerAuth"
        const val TAG_USERS = "Users"
        const val TAG_OPERATIONS = "Operations"
        const val PROBLEM_SCHEMA = "Problem"
        const val FIELD_VIOLATION_SCHEMA = "FieldViolation"
        private const val MIN_STATUS = 400
        private const val MAX_STATUS = 599

        private val PROBLEM_TYPES =
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
