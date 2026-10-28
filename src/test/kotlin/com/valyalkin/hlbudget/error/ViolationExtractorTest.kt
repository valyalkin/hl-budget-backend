package com.valyalkin.hlbudget.error

import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.LogicalType
import tools.jackson.module.kotlin.KotlinModule

/**
 * These tests are the contract behind "an error must name the field". Each one asserts the pointer
 * and the code, not just that something was rejected.
 */
class ViolationExtractorTest {
    private val extractor = ViolationExtractor()

    private data class Line(
        @field:Positive val quantity: Int,
    )

    private data class Order(
        @field:NotBlank val reference: String,
        val lines: List<Line>,
    )

    /**
     * Configured exactly like the production mapper (see config/JacksonConfig.kt): the Kotlin
     * module, and FAIL_ON_UNKNOWN_PROPERTIES switched on - Jackson 3 disables it by default, so
     * without that line an unknown property is silently dropped instead of reported.
     */
    private val mapper: ObjectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(LogicalType.Textual) { config ->
                config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            }.build()

    @Test
    fun `a field error becomes a pointer, a field, a code and a message`() {
        val binding = BeanPropertyBindingResult(Order("", emptyList()), "order")
        binding.rejectValue("reference", "NotBlank", "must not be blank")

        val violations = extractor.fromFieldErrors(binding.fieldErrors, binding.globalErrors)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.pointer).isEqualTo("#/reference")
            assertThat(it.field).isEqualTo("reference")
            assertThat(it.code).isEqualTo("NotBlank")
            assertThat(it.message).isEqualTo("must not be blank")
        })
    }

    @Test
    fun `a nested, indexed field keeps its position in the pointer`() {
        // Built directly rather than via rejectValue: resolving an indexed path against a shorter
        // list makes Spring try to auto-grow the bean, which is not what is under test here.
        val fieldError =
            FieldError(
                "order",
                "lines[2].quantity",
                null,
                false,
                arrayOf("Positive"),
                null,
                "must be greater than 0",
            )

        val violations = extractor.fromFieldErrors(listOf(fieldError), emptyList())

        assertThat(violations).singleElement().satisfies({
            assertThat(it.field).isEqualTo("lines[2].quantity")
            assertThat(it.pointer).isEqualTo("#/lines/2/quantity")
        })
    }

    @Test
    fun `an object-level error points at the document root`() {
        val binding = BeanPropertyBindingResult(Order("ref", emptyList()), "order")
        binding.reject("AmountsMustBalance", "amounts must balance")

        val violations = extractor.fromFieldErrors(binding.fieldErrors, binding.globalErrors)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.pointer).isEqualTo("#")
            assertThat(it.code).isEqualTo("AmountsMustBalance")
        })
    }

    @Test
    fun `constraint violations are reported with the annotation name as the code`() {
        val validator = Validation.buildDefaultValidatorFactory().validator
        val failures = validator.validate(Order("", emptyList()))
        val exception = jakarta.validation.ConstraintViolationException(failures)

        val violations = extractor.fromConstraintViolations(exception)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.field).isEqualTo("reference")
            assertThat(it.pointer).isEqualTo("#/reference")
            assertThat(it.code).isEqualTo("NotBlank")
        })
    }

    @Test
    fun `an unknown property is named rather than dismissed as malformed JSON`() {
        val failure =
            runCatching { mapper.readValue("""{"reference":"r","lines":[],"typo":1}""", Order::class.java) }
                .exceptionOrNull()

        assertThat(failure).isInstanceOf(UnrecognizedPropertyException::class.java)

        val violations = extractor.fromJacksonFailure(failure)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.field).isEqualTo("typo")
            assertThat(it.code).isEqualTo("Unknown")
            assertThat(it.message).contains("not a recognised property")
        })
    }

    @Test
    fun `a missing required field is reported as Required, by name`() {
        val failure =
            runCatching { mapper.readValue("""{"lines":[]}""", Order::class.java) }
                .exceptionOrNull()

        val violations = extractor.fromJacksonFailure(failure)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.code).isEqualTo("Required")
            assertThat(it.message).isEqualTo("is required")
        })
    }

    @Test
    fun `a wrong type names the expected type and never echoes the value`() {
        val failure =
            runCatching {
                mapper.readValue("""{"reference":"r","lines":[{"quantity":"abc"}]}""", Order::class.java)
            }.exceptionOrNull()

        val violations = extractor.fromJacksonFailure(failure)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.code).isEqualTo("TypeMismatch")
            assertThat(it.pointer).isEqualTo("#/lines/0/quantity")
            assertThat(it.message).contains("must be a valid")
            assertThat(it.message).doesNotContain("abc")
        })
    }

    @Test
    fun `a JSON number for a string field is a TypeMismatch, not a silent coercion`() {
        val failure =
            runCatching { mapper.readValue("""{"reference":84.37,"lines":[]}""", Order::class.java) }
                .exceptionOrNull()

        assertThat(extractor.fromJacksonFailure(failure)).singleElement().satisfies({
            assertThat(it.code).isEqualTo("TypeMismatch")
            assertThat(it.pointer).isEqualTo("#/reference")
            assertThat(it.message).doesNotContain("84.37")
        })
    }

    @Test
    fun `an object where a string belongs is a TypeMismatch on that field`() {
        val failure =
            runCatching { mapper.readValue("""{"reference":{},"lines":[]}""", Order::class.java) }
                .exceptionOrNull()

        assertThat(extractor.fromJacksonFailure(failure)).singleElement().satisfies({
            assertThat(it.code).isEqualTo("TypeMismatch")
            assertThat(it.pointer).isEqualTo("#/reference")
        })
    }

    @Test
    fun `a field error with no code or message still produces a usable violation`() {
        // Spring allows both to be null. The fallbacks matter: a violation with an empty code is
        // useless to a client branching on it.
        val bare = FieldError("order", "reference", "rejected", false, null, null, null)

        val violations = extractor.fromFieldErrors(listOf(bare), emptyList())

        assertThat(violations).singleElement().satisfies({
            assertThat(it.code).isEqualTo("Invalid")
            assertThat(it.message).isEqualTo("is invalid")
            assertThat(it.pointer).isEqualTo("#/reference")
        })
    }

    @Test
    fun `an object error with no code or message falls back the same way`() {
        val bare = ObjectError("order", null, null, null)

        val violations = extractor.fromFieldErrors(emptyList(), listOf(bare))

        assertThat(violations).singleElement().satisfies({
            assertThat(it.field).isEqualTo("order")
            assertThat(it.pointer).isEqualTo("#")
            assertThat(it.code).isEqualTo("Invalid")
            assertThat(it.message).isEqualTo("is invalid")
        })
    }

    @Test
    fun `a Jackson failure with no path at all is still attributed to the body`() {
        // Hand-built because a parse failure that carries no path is hard to provoke, and the
        // fallback is what stops the response from naming no field whatsoever.
        val noPath = MismatchedInputException.from(null, String::class.java, "no path")

        val violations = extractor.fromJacksonFailure(noPath)

        assertThat(violations).singleElement().satisfies({
            assertThat(it.field).isEqualTo("body")
            assertThat(it.pointer).isEqualTo("#/body")
        })
    }

    @Test
    fun `an unrecognised cause yields no violations, so the handler answers 400 malformed`() {
        assertThat(extractor.fromJacksonFailure(null)).isEmpty()
        assertThat(extractor.fromJacksonFailure(IllegalStateException("boom"))).isEmpty()
    }
}
