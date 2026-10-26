package com.valyalkin.hlbudget.error

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.springframework.stereotype.Component
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.method.annotation.HandlerMethodValidationException
import tools.jackson.core.JacksonException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.databind.exc.ValueInstantiationException

/**
 * Turns the framework's many validation failure types into one list of [FieldViolation].
 *
 * This is the part that makes an error informative: every branch here names the offending field
 * and nothing here passes a framework message through verbatim.
 */
@Component
class ViolationExtractor {
    /** Bean Validation on an @Valid @RequestBody. */
    fun fromFieldErrors(
        fieldErrors: List<FieldError>,
        globalErrors: List<ObjectError>,
    ): List<FieldViolation> =
        fieldErrors.map { error ->
            FieldViolation(
                pointer = pointerOf(error.field),
                field = error.field,
                code = error.code ?: "Invalid",
                message = error.defaultMessage ?: "is invalid",
            )
        } +
            globalErrors.map { error ->
                FieldViolation(
                    pointer = "#",
                    field = error.objectName,
                    code = error.code ?: "Invalid",
                    message = error.defaultMessage ?: "is invalid",
                )
            }

    /** Bean Validation on a @RequestParam/@PathVariable, or on a @Validated service method. */
    fun fromConstraintViolations(exception: ConstraintViolationException): List<FieldViolation> =
        exception.constraintViolations.orEmpty().map(::toViolation)

    /** Bean Validation applied directly to handler method arguments (Spring Framework 6.1+). */
    fun fromHandlerMethodValidation(exception: HandlerMethodValidationException): List<FieldViolation> =
        exception.parameterValidationResults.flatMap { result ->
            val name = result.methodParameter.parameterName ?: "argument"
            result.resolvableErrors.map { error ->
                FieldViolation(
                    pointer = pointerOf(name),
                    field = name,
                    code = error.codes?.firstOrNull()?.substringAfterLast('.') ?: "Invalid",
                    message = error.defaultMessage ?: "is invalid",
                )
            }
        }

    /**
     * A body Jackson could not bind. The interesting cases are distinguished because "malformed
     * JSON" is not an answer a client can act on:
     *  - an unknown property     -> name it, so a typo is obvious
     *  - a missing required one  -> name it, rather than reporting a generic parse failure
     *  - a wrong type            -> name it and say what was expected, never echoing the value
     */
    fun fromJacksonFailure(cause: Throwable?): List<FieldViolation> =
        when (cause) {
            is UnrecognizedPropertyException -> {
                listOf(
                    violation(cause, "Unknown", "is not a recognised property", cause.propertyName),
                )
            }

            is InvalidFormatException -> {
                listOf(
                    violation(cause, "TypeMismatch", "must be a valid ${cause.targetType.simpleName}"),
                )
            }

            is MismatchedInputException -> {
                listOf(
                    violation(cause, "Required", "is required"),
                )
            }

            // Jackson 3 does not derive this one from MismatchedInputException, so it needs its own
            // branch - without it a required field rejected by Kotlin's own null check would be
            // reported as a generic parse failure with no field name.
            is ValueInstantiationException -> {
                listOf(
                    violation(cause, "Required", "is required"),
                )
            }

            else -> {
                emptyList()
            }
        }

    private fun violation(
        exception: JacksonException,
        code: String,
        message: String,
        fallbackField: String? = null,
    ): FieldViolation {
        val field = fieldOf(exception) ?: fallbackField ?: "body"
        return FieldViolation(
            pointer = pointerOf(field),
            field = field,
            code = code,
            message = message,
        )
    }

    private fun toViolation(violation: ConstraintViolation<*>): FieldViolation {
        val field = violation.propertyPath.toString().substringAfter('.', violation.propertyPath.toString())
        return FieldViolation(
            pointer = pointerOf(field),
            field = field,
            code = violation.constraintDescriptor.annotation.annotationClass.simpleName ?: "Invalid",
            message = violation.message,
        )
    }

    /**
     * Jackson reports the location as a path of field names and array indices; joining it keeps
     * nested and repeated structures distinguishable (`lines[2].quantity`).
     */
    private fun fieldOf(exception: JacksonException): String? =
        exception.path
            .takeIf { it.isNotEmpty() }
            ?.joinToString("") { reference ->
                val property = reference.propertyName
                when {
                    property != null -> ".$property"
                    reference.index >= 0 -> "[${reference.index}]"
                    else -> ""
                }
            }?.removePrefix(".")

    /** `lines[2].quantity` -> `#/lines/2/quantity`, per RFC 6901. */
    private fun pointerOf(field: String): String =
        "#/" +
            field
                .replace("[", ".")
                .replace("]", "")
                .split('.')
                .filter { it.isNotEmpty() }
                .joinToString("/")
}
