package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.BEARER_SCHEME
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.FIELD_VIOLATION_SCHEMA
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.HEALTH_SCHEMA
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.PROBLEM_SCHEMA
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.REQUEST_ID_HEADER
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.REQUEST_ID_HEADER_REF
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.TAG_OPERATIONS
import com.valyalkin.hlbudget.error.ProblemCodes
import com.valyalkin.hlbudget.error.ProblemDetailFactory
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus

/** Shared components of the OpenAPI document: schemas, headers, examples, error responses, probes. */
internal object OpenApiComponents {
    fun components(): Components =
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
            .addSchemas(FIELD_VIOLATION_SCHEMA, fieldViolationSchema())
            .addSchemas(HEALTH_SCHEMA, OpenApiProbes.healthSchema())
            .addHeaders(
                REQUEST_ID_HEADER,
                Header()
                    .description("Trace reference for this request; equals `traceId` in any problem body")
                    .schema(StringSchema().example(EXAMPLE_TRACE_ID)),
            ).addExamples("ProfileFirstVisit", profileExample(first = true))
            .addExamples("ProfileReturning", profileExample(first = false))
            .addResponses(
                "Unauthenticated",
                problemResponse(
                    "Missing, malformed, expired, wrongly signed, wrong-issuer or wrong-audience token. " +
                        "No user data is returned or created.",
                    UNAUTHORIZED,
                    "A valid bearer token is required",
                    withAuthenticateHeader = true,
                ),
            ).addResponses(
                "InternalError",
                problemResponse(
                    "Unexpected failure. Cause is logged against traceId and never returned",
                    INTERNAL,
                    ProblemDetailFactory.SAFE_SERVER_DETAIL,
                ),
            ).addResponses(
                "ServiceUnavailable",
                problemResponse(
                    "A required dependency (the database) is unreachable",
                    UNAVAILABLE,
                    ProblemDetailFactory.UNAVAILABLE_DETAIL,
                ),
            )

    private fun problemResponse(
        description: String,
        status: Int,
        detail: String,
        withAuthenticateHeader: Boolean = false,
    ): ApiResponse {
        val response =
            ApiResponse()
                .description(description)
                .addHeaderObject(REQUEST_ID_HEADER, Header().`$ref`(REQUEST_ID_HEADER_REF))
                .content(
                    Content().addMediaType(
                        "application/problem+json",
                        MediaType().schema(ref(PROBLEM_SCHEMA)).example(problemExample(status, detail)),
                    ),
                )
        if (withAuthenticateHeader) {
            response.addHeaderObject("WWW-Authenticate", Header().schema(StringSchema().example("Bearer")))
        }
        return response
    }

    private fun problemExample(
        status: Int,
        detail: String,
    ): Map<String, Any> =
        linkedMapOf(
            "type" to ProblemDetailFactory.TYPE_PREFIX + ProblemCodes.forStatus(status),
            "title" to (HttpStatus.resolve(status)?.reasonPhrase ?: "Error"),
            "status" to status,
            "detail" to detail,
            "instance" to "/api/v1/users/me",
            "timestamp" to EXAMPLE_TIME,
            "traceId" to EXAMPLE_TRACE_ID,
        )

    private fun profileExample(first: Boolean): Example =
        Example()
            .summary(
                if (first) {
                    "First call creates the record"
                } else {
                    "Later call refreshes lastSeenAt; no avatar at the identity provider"
                },
            ).value(
                linkedMapOf(
                    "id" to "0b9f6c1e-3c1a-4f2e-9a57-2f1d6c2b8e11",
                    "displayName" to "Alex Example",
                    "email" to "alex@example.com",
                    "avatarUrl" to if (first) "https://lh3.googleusercontent.com/a/example" else null,
                    "firstSeenAt" to EXAMPLE_TIME,
                    "lastSeenAt" to if (first) EXAMPLE_TIME else "2026-10-26T18:02:41Z",
                ),
            )

    private fun ref(schema: String): Schema<Any> = Schema<Any>().`$ref`("#/components/schemas/$schema")

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

    private const val EXAMPLE_TRACE_ID = "4be0baf6ee174255"
    private const val EXAMPLE_TIME = "2026-10-25T09:15:00Z"
    private const val UNAUTHORIZED = 401
    private const val INTERNAL = 500
    private const val UNAVAILABLE = 503
    private const val MIN_STATUS = 400
    private const val MAX_STATUS = 599
    private val PROBLEM_TYPES = OpenApiConfig.PROBLEM_TYPES
}

/** The probes are actuator additional paths, invisible to springdoc, so they are documented here. */
internal object OpenApiProbes {
    /** The probes are actuator additional paths, so springdoc cannot see them; documented by hand. */
    fun probePaths(): Paths =
        Paths()
            .addPathItem(
                "/healthz",
                PathItem().get(probe("liveness", "Liveness probe; checks no dependencies", ready = false)),
            ).addPathItem(
                "/readyz",
                PathItem().get(
                    probe("readiness", "Readiness probe; fails while Postgres is unreachable", ready = true),
                ),
            )

    private fun probe(
        id: String,
        summary: String,
        ready: Boolean,
    ): Operation {
        val responses =
            ApiResponses().addApiResponse(
                "200",
                healthResponse(if (ready) "Ready to serve" else "Process is alive", "UP"),
            )
        if (ready) {
            responses.addApiResponse(
                "503",
                healthResponse("Not ready (database unreachable). No details are disclosed", "DOWN"),
            )
        }
        return Operation()
            .tags(listOf(TAG_OPERATIONS))
            .operationId(id)
            .summary(summary)
            .security(emptyList())
            .responses(responses)
    }

    private fun healthResponse(
        description: String,
        status: String,
    ): ApiResponse =
        ApiResponse()
            .description(description)
            .content(
                Content().addMediaType(
                    "application/json",
                    MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$HEALTH_SCHEMA")).example(
                        mapOf("status" to status),
                    ),
                ),
            )

    fun healthSchema(): Schema<Any> =
        ObjectSchema()
            .addProperty("status", StringSchema().apply { enum = listOf("UP", "DOWN") })
            .apply { required = listOf("status") }
}
