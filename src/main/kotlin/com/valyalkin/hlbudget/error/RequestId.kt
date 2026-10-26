package com.valyalkin.hlbudget.error

import jakarta.servlet.http.HttpServletRequest

/**
 * Where the traceId on a problem response comes from: the attribute the request-id filter sets,
 * or the inbound header when the filter did not run (a container error dispatch).
 */
object RequestId {
    const val ATTRIBUTE: String = "com.valyalkin.hlbudget.requestId"
    const val HEADER: String = "X-Request-Id"
    const val MDC_KEY: String = "traceId"

    fun of(request: HttpServletRequest): String =
        request.getAttribute(ATTRIBUTE) as? String
            ?: request.getHeader(HEADER)
            ?: "unknown"
}
