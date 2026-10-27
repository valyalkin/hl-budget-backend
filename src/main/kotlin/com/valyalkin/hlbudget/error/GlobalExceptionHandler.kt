package com.valyalkin.hlbudget.error

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * The one place an exception becomes a response. Every handler returns a plain [ProblemDetail];
 * 4xx detail names fields only, 5xx detail is fixed and the cause is logged against the traceId.
 */
@RestControllerAdvice
class GlobalExceptionHandler(
    private val problems: ProblemDetailFactory,
    private val violations: ViolationExtractor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ---------------------------------------------------------------- the service's own failures

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(
        exception: BusinessException,
        request: HttpServletRequest,
    ): ProblemDetail {
        log.warn(
            "{} on {} {} (traceId={}): {} {}",
            exception.status.value(),
            request.method,
            request.requestURI,
            RequestId.of(request),
            exception.message,
            exception.details,
        )
        if (exception is ValidationException) {
            return problems.create(exception.status, exception.message, request, violations = exception.violations)
        }
        return problems.from(exception, request)
    }

    @ExceptionHandler(SystemException::class)
    fun handleSystem(
        exception: SystemException,
        request: HttpServletRequest,
    ): ProblemDetail {
        logServerFailure(exception.status, exception.details, exception, request)
        return problems.serverError(exception.status, request)
    }

    /**
     * The database is unreachable (connection refused, pool timeout, no transaction possible).
     * CannotGetJdbcConnectionException is a DataAccessResourceFailureException, so it lands here.
     */
    @ExceptionHandler(DataAccessResourceFailureException::class, CannotCreateTransactionException::class)
    fun handleDatabaseUnavailable(
        exception: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        logServerFailure(HttpStatus.SERVICE_UNAVAILABLE, emptyMap(), exception, request)
        return problems.serverError(HttpStatus.SERVICE_UNAVAILABLE, request)
    }

    // ------------------------------------------------------------------------ request validation

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ProblemDetail =
        validationProblem(
            violations.fromFieldErrors(exception.bindingResult.fieldErrors, exception.bindingResult.globalErrors),
            request,
        )

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleParameterValidation(
        exception: HandlerMethodValidationException,
        request: HttpServletRequest,
    ): ProblemDetail = validationProblem(violations.fromHandlerMethodValidation(exception), request)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: HttpServletRequest,
    ): ProblemDetail = validationProblem(violations.fromConstraintViolations(exception), request)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(
        exception: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val found = violations.fromJacksonFailure(exception.cause)
        return if (found.isEmpty()) {
            problems.create(HttpStatus.BAD_REQUEST, "The request body is not valid JSON.", request)
        } else {
            validationProblem(found, request)
        }
    }

    /**
     * A query parameter or header of the wrong type is a field the caller can fix (422). A path
     * segment that is not the right type (`/spendings/abc`) has no field to point at: the URL
     * itself is malformed (400, research R-009).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        exception: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val expected = exception.requiredType?.simpleName ?: "the expected type"
        if (exception.parameter.hasParameterAnnotation(PathVariable::class.java)) {
            return problems.create(
                HttpStatus.BAD_REQUEST,
                "The path segment ${exception.name} must be a valid $expected.",
                request,
            )
        }
        return validationProblem(
            listOf(FieldViolation("#/${exception.name}", exception.name, "TypeMismatch", "must be a valid $expected")),
            request,
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(
        exception: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ProblemDetail =
        validationProblem(
            listOf(
                FieldViolation(
                    "#/${exception.parameterName}",
                    exception.parameterName,
                    "Required",
                    "is a required ${exception.parameterType} query parameter",
                ),
            ),
            request,
        )

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(
        exception: MissingRequestHeaderException,
        request: HttpServletRequest,
    ): ProblemDetail =
        validationProblem(
            listOf(
                FieldViolation("#/${exception.headerName}", exception.headerName, "Required", "is a required header"),
            ),
            request,
        )

    // ------------------------------------------------------------------------- protocol failures

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(
        exception: NoResourceFoundException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(HttpStatus.NOT_FOUND, "No endpoint ${request.method} ${request.requestURI}.", request)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        exception: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val supported = exception.supportedMethods?.joinToString(", ").orEmpty()
        return problems.create(
            HttpStatus.METHOD_NOT_ALLOWED,
            "${exception.method} is not supported here. Supported: $supported.",
            request,
        )
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(
        exception: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Content-Type ${exception.contentType} is not supported. Use application/json.",
            request,
        )

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleNotAcceptable(
        exception: HttpMediaTypeNotAcceptableException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            HttpStatus.NOT_ACCEPTABLE,
            "No acceptable representation; this endpoint produces application/json.",
            request,
        )

    // ---------------------------------------------------------------------------------- security

    /** Method security rejecting a call inside the dispatcher; filter-chain rejections never get here. */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        exception: AccessDeniedException,
        request: HttpServletRequest,
    ): ProblemDetail {
        val authentication = SecurityContextHolder.getContext().authentication
        val anonymous = authentication == null || authentication is AnonymousAuthenticationToken
        val status = if (anonymous) HttpStatus.UNAUTHORIZED else HttpStatus.FORBIDDEN
        return problems.create(status, status.reasonPhrase, request)
    }

    // -------------------------------------------------------------------------------- the rest

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(
        exception: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ProblemDetail {
        // The text names constraints and often values: logged, never returned.
        log.warn("409 data integrity violation (traceId={})", RequestId.of(request), exception)
        return problems.create(
            HttpStatus.CONFLICT,
            "The request conflicts with the current state of the resource.",
            request,
        )
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLocking(
        exception: OptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ProblemDetail =
        problems.create(
            HttpStatus.CONFLICT,
            "The resource was modified concurrently. Re-read it and retry.",
            request,
            details = mapOf("reason" to "concurrent-modification"),
        )

    /** The backstop. Anything unmapped is a bug in this service. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ProblemDetail {
        logServerFailure(HttpStatus.INTERNAL_SERVER_ERROR, emptyMap(), exception, request)
        return problems.serverError(HttpStatus.INTERNAL_SERVER_ERROR, request)
    }

    // ---------------------------------------------------------------------------------- helpers

    private fun validationProblem(
        found: List<FieldViolation>,
        request: HttpServletRequest,
    ): ProblemDetail {
        log.warn(
            "422 validation failed on {} {} (traceId={}): {}",
            request.method,
            request.requestURI,
            RequestId.of(request),
            found.joinToString { it.field },
        )
        val detail = if (found.isEmpty()) "The request is invalid." else ValidationException.describe(found)
        return problems.create(HttpStatus.UNPROCESSABLE_ENTITY, detail, request, violations = found)
    }

    private fun logServerFailure(
        status: HttpStatus,
        details: Map<String, Any>,
        exception: Throwable,
        request: HttpServletRequest,
    ) {
        log.error(
            "{} on {} {} (traceId={}) details={}",
            status.value(),
            request.method,
            request.requestURI,
            RequestId.of(request),
            details,
            exception,
        )
    }
}
