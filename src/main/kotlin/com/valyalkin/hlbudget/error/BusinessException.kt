package com.valyalkin.hlbudget.error

import org.springframework.http.HttpStatus

/**
 * A request the caller can fix: the client is wrong, not the service. Always a 4xx.
 *
 * Throw it from `service/` only. Controllers do not decide status codes and repositories do not
 * know about HTTP; this is the single channel through which business rules become responses.
 *
 * The status is a property of the subclass, so a throw site cannot get it wrong. Everything else
 * the response needs is just the two arguments:
 *
 * ```kotlin
 * throw NotFoundException(
 *     "Invoice $id was not found",
 *     mapOf("resource" to "invoice", "invoiceId" to id),
 * )
 * ```
 *
 * [message] becomes the problem `detail` - prose, for a person reading a log or a stack trace.
 * [details] is serialised as the `details` object on the response, which is where a client finds
 * something it can act on without parsing English.
 *
 * Two rules for what goes in [details], neither enforced by the compiler:
 *
 *  - **Scalars only** - strings, numbers, booleans, enums, UUIDs, instants, or lists of those.
 *    Never an entity, a DTO or anything holding a lazy association: it is serialised straight into
 *    the response, so a JPA proxy here is a `LazyInitializationException` at the error path, which
 *    is the worst possible moment for a second failure.
 *  - **Nothing secret, and nothing the caller did not already send.** The map is an error response;
 *    assume it ends up in a log aggregator the caller does not control.
 */
open class BusinessException(
    val status: HttpStatus,
    override val message: String,
    val details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    init {
        require(status.is4xxClientError) {
            "BusinessException describes a client error, but was given $status"
        }
    }
}

/** The addressed resource does not exist. 404. */
class NotFoundException(
    message: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : BusinessException(HttpStatus.NOT_FOUND, message, details, cause)

/** The resource already exists, or the write would break a uniqueness rule. 409. */
class DuplicateException(
    message: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : BusinessException(HttpStatus.CONFLICT, message, details, cause)

/**
 * The write is refused by the current state of something it depends on - deleting an account that
 * spendings still use, for instance. 409. Unlike [DuplicateException] it is not about uniqueness,
 * and unlike [UnprocessableEntityException] the caller can fix it by changing that state. Always
 * put a `reason` in [details].
 */
class ConflictException(
    message: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : BusinessException(HttpStatus.CONFLICT, message, details, cause)

/**
 * The request was well formed and the resource exists, but a business rule refuses it - paying an
 * already-paid invoice, for instance. 422, which is what distinguishes "I cannot parse this" (400)
 * from "I understood you and the answer is no".
 *
 * Because the `type` of a 422 only says "unprocessable", [details] is how the client learns *which*
 * rule refused. Put a discriminator in it - `mapOf("reason" to "already-paid")` - whenever more
 * than one rule can produce the same status.
 */
open class UnprocessableEntityException(
    message: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, message, details, cause)

/** The caller's credentials are not usable. 401, rendered as `unauthenticated`. */
class UnauthenticatedException(
    message: String,
    details: Map<String, Any> = emptyMap(),
) : BusinessException(HttpStatus.UNAUTHORIZED, message, details)

/**
 * One or more request fields broke a rule that only the service can check - a date after "today"
 * in the app's time zone, a reference to a category the caller does not own. 422, rendered with
 * the same `errors` array as a Bean Validation failure, so a client sees one shape for "fix these
 * fields" whichever layer found them (research R-009).
 */
class ValidationException(
    val violations: List<FieldViolation>,
) : UnprocessableEntityException(describe(violations)) {
    companion object {
        /** The problem `detail` for a list of violations; shared with the handler. */
        fun describe(violations: List<FieldViolation>): String {
            require(violations.isNotEmpty()) { "A ValidationException needs at least one violation" }
            return when (violations.size) {
                1 -> "1 field is invalid: ${violations.first().field}"
                else -> "${violations.size} fields are invalid: ${violations.joinToString { it.field }}"
            }
        }
    }
}
