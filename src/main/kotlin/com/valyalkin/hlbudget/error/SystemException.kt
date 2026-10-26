package com.valyalkin.hlbudget.error

import org.springframework.http.HttpStatus

/**
 * The service or something it depends on failed. Always a 5xx, and never the caller's fault.
 *
 * [message] and [details] are both for the log. Neither reaches the client: GlobalExceptionHandler
 * replaces the detail of every 5xx with a fixed, safe string and omits the `details` object
 * entirely, correlating the two through the traceId. That asymmetry with [BusinessException] is
 * deliberate - a 4xx describes the caller's own request back to them, while a 5xx describes this
 * service's internals, and those are exactly what must not be published.
 */
class SystemException(
    override val message: String,
    val details: Map<String, Any> = emptyMap(),
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    init {
        require(status.is5xxServerError) {
            "SystemException describes a server failure, but was given $status"
        }
    }
}
