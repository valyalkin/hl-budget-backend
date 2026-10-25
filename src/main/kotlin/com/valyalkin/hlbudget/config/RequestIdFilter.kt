package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.RequestId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.SecureRandom
import java.util.HexFormat

/**
 * Gives every request one id, used in three places: MDC (`traceId`, so every log line carries it),
 * the `X-Request-Id` response header, and the `traceId` of any problem response.
 *
 * An inbound `X-Request-Id` is reused when it looks like an id (short, no odd characters), so a
 * trace survives across services without letting a caller inject text into logs or headers.
 *
 * Ordered ahead of Spring Security's chain (-100), or rejected requests would carry no id.
 */
@Component
@Order(FILTER_ORDER)
class RequestIdFilter : OncePerRequestFilter() {
    private val random = SecureRandom()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(RequestId.HEADER)?.takeIf { SAFE_ID.matches(it) } ?: newId()
        request.setAttribute(RequestId.ATTRIBUTE, requestId)
        response.setHeader(RequestId.HEADER, requestId)
        MDC.put(RequestId.MDC_KEY, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(RequestId.MDC_KEY)
        }
    }

    private fun newId(): String = HexFormat.of().formatHex(ByteArray(ID_BYTES).also(random::nextBytes))

    private companion object {
        const val ID_BYTES = 8
        val SAFE_ID = Regex("^[A-Za-z0-9._-]{1,64}$")
    }
}

internal const val FILTER_ORDER = -110
