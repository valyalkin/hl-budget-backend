package com.valyalkin.hlbudget.error

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.method.ParameterErrors
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.method.annotation.HandlerMethodValidationException
import tools.jackson.core.JacksonException
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.databind.exc.ValueInstantiationException
import tools.jackson.module.kotlin.KotlinInvalidNullException

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

    /**
     * Bean Validation applied to handler method arguments (Spring Framework 6.1+). Spring takes
     * this path for the whole method as soon as any parameter carries a constraint, so it covers
     * an `@Valid` body too: that one is unpacked field by field, exactly as [fromFieldErrors] does.
     * A header or query parameter is named as the client sent it (`Idempotency-Key`), not by the
     * Kotlin parameter name.
     */
    fun fromHandlerMethodValidation(exception: HandlerMethodValidationException): List<FieldViolation> =
        exception.parameterValidationResults.flatMap { result ->
            if (result is ParameterErrors) {
                fromFieldErrors(result.fieldErrors, result.globalErrors)
            } else {
                val name = requestName(result.methodParameter)
                result.resolvableErrors.map { error ->
                    FieldViolation(
                        pointer = pointerOf(name),
                        field = name,
                        // The last code is the bare constraint name ("Size"); the others are qualified.
                        code = error.codes?.lastOrNull() ?: "Invalid",
                        message = error.defaultMessage ?: "is invalid",
                    )
                }
            }
        }

    private fun requestName(parameter: MethodParameter): String {
        val header = parameter.getParameterAnnotation(RequestHeader::class.java)
        val query = parameter.getParameterAnnotation(RequestParam::class.java)
        return listOfNotNull(header?.name, header?.value, query?.name, query?.value, parameter.parameterName)
            .firstOrNull { it.isNotBlank() } ?: "argument"
    }

    /**
     * A body Jackson could not bind. The interesting cases are distinguished because "malformed
     * JSON" is not an answer a client can act on:
     *  - an unknown property     -> name it, so a typo is obvious
     *  - a missing required one  -> name it, rather than reporting a generic parse failure
     *  - a wrong type            -> name it and say what was expected, never echoing the value
     *    (including a JSON number for a string field: JacksonConfig refuses that coercion)
     */
    fun fromJacksonFailure(cause: Throwable?): List<FieldViolation> =
        when (cause) {
            is UnrecognizedPropertyException -> {
                listOf(
                    violation(cause, "Unknown", "is not a recognised property", cause.propertyName),
                )
            }

            // Kotlin's own null check on a non-nullable property: the field is absent or null.
            is KotlinInvalidNullException -> {
                listOf(
                    violation(cause, "Required", "is required"),
                )
            }

            is InvalidFormatException -> {
                listOf(
                    violation(cause, "TypeMismatch", "must be a valid ${cause.targetType.simpleName}"),
                )
            }

            // With a path, the field is there but has the wrong JSON shape (an object where a string
            // belongs). Without one, the body itself is missing.
            is MismatchedInputException -> {
                listOf(
                    if (cause.path.isNotEmpty()) {
                        violation(cause, "TypeMismatch", "must be a valid ${cause.targetType?.simpleName ?: "value"}")
                    } else {
                        violation(cause, "Required", "is required")
                    },
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
