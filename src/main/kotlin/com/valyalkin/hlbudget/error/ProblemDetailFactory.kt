package com.valyalkin.hlbudget.error

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

/**
 * Builds the one error shape this service produces: RFC 9457 problem+json with `instance`,
 * `timestamp` (UTC) and `traceId`. Fields are copied in explicitly, so no exception text can
 * reach a client by accident.
 */
@Component
class ProblemDetailFactory {
    fun create(
        status: HttpStatusCode,
        detail: String,
        request: HttpServletRequest,
        violations: List<FieldViolation> = emptyList(),
        details: Map<String, Any> = emptyMap(),
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            type = URI.create(TYPE_PREFIX + ProblemCodes.forStatus(status.value()))
            title = titleFor(status)
            instance = URI.create(request.requestURI)
            setProperty("timestamp", Instant.now())
            setProperty("traceId", RequestId.of(request))
            if (violations.isNotEmpty()) {
                setProperty("errors", violations)
            }
            if (details.isNotEmpty()) {
                setProperty("details", details)
            }
        }

    /** A 4xx the service raised itself; its details map describes the caller's own request. */
    fun from(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ProblemDetail =
        create(
            status = exception.status,
            detail = exception.message,
            request = request,
            details = exception.details,
        )

    /** A 5xx never describes what broke: a fixed detail, no details map. */
    fun serverError(
        status: HttpStatusCode,
        request: HttpServletRequest,
    ): ProblemDetail = create(status, safeDetailFor(status), request)

    companion object {
        const val TYPE_PREFIX: String = "urn:problem:hl-budget:"
        const val SAFE_SERVER_DETAIL: String = "An unexpected error occurred"
        const val UNAVAILABLE_DETAIL: String = "The service is temporarily unavailable"

        fun safeDetailFor(status: HttpStatusCode): String =
            if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) UNAVAILABLE_DETAIL else SAFE_SERVER_DETAIL

        private fun titleFor(status: HttpStatusCode): String =
            HttpStatus.resolve(status.value())?.reasonPhrase ?: "Error"
    }
}
