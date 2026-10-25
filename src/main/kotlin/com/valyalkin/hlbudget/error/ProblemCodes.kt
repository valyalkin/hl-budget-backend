package com.valyalkin.hlbudget.error

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode

/**
 * The `type` URN categories this service emits (contracts/openapi.yaml `Problem.type`).
 *
 * The category is derived from the status, so it is stable API surface. Anything a client needs
 * to branch on beyond the category goes in the problem's `details` map, as a `reason` key.
 */
object ProblemCodes {
    const val UNAUTHENTICATED = "unauthenticated"
    const val ACCESS_DENIED = "access-denied"
    const val BAD_REQUEST = "bad-request"
    const val NOT_FOUND = "not-found"
    const val CONFLICT = "conflict"
    const val UNPROCESSABLE_ENTITY = "unprocessable-entity"
    const val INTERNAL_ERROR = "internal-error"
    const val SERVICE_UNAVAILABLE = "service-unavailable"

    /** Matched on the numeric code: several statuses have more than one HttpStatus constant. */
    fun forStatus(status: Int): String =
        when (status) {
            HttpStatus.UNAUTHORIZED.value() -> UNAUTHENTICATED
            HttpStatus.FORBIDDEN.value() -> ACCESS_DENIED
            HttpStatus.NOT_FOUND.value() -> NOT_FOUND
            HttpStatus.CONFLICT.value() -> CONFLICT
            HttpStatus.UNPROCESSABLE_ENTITY.value() -> UNPROCESSABLE_ENTITY
            HttpStatus.SERVICE_UNAVAILABLE.value() -> SERVICE_UNAVAILABLE
            else -> if (HttpStatusCode.valueOf(status).is4xxClientError) BAD_REQUEST else INTERNAL_ERROR
        }
}
