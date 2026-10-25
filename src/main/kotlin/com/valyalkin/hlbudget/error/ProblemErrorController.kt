package com.valyalkin.hlbudget.error

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.webmvc.error.ErrorAttributes
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.ServletWebRequest

/**
 * Container error dispatches (a failure in a filter, say) never reach an @ExceptionHandler.
 * Replacing Boot's BasicErrorController keeps even those responses problem+json.
 */
@RestController
class ProblemErrorController(
    private val problems: ProblemDetailFactory,
    private val errorAttributes: ErrorAttributes,
) : ErrorController {
    private val log = LoggerFactory.getLogger(javaClass)

    @RequestMapping("\${server.error.path:\${error.path:/error}}")
    fun handleError(request: HttpServletRequest): ProblemDetail {
        val status = statusOf(request)
        if (status.is5xxServerError) {
            val attributes =
                errorAttributes.getErrorAttributes(
                    ServletWebRequest(request),
                    ErrorAttributeOptions.defaults(),
                )
            log.error(
                "{} error dispatch for {} (traceId={}): {}",
                status.value(),
                attributes["path"],
                RequestId.of(request),
                attributes["error"],
            )
            return problems.serverError(status, request)
        }
        return problems.create(status, HttpStatus.resolve(status.value())?.reasonPhrase ?: "Error", request)
    }

    private fun statusOf(request: HttpServletRequest): HttpStatusCode {
        val code =
            request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int
                ?: return HttpStatus.INTERNAL_SERVER_ERROR
        return runCatching { HttpStatusCode.valueOf(code) }.getOrElse { HttpStatus.INTERNAL_SERVER_ERROR }
    }
}
