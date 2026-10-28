package com.valyalkin.hlbudget.error

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus

class ExceptionHierarchyTest {
    @Test
    fun `each subclass pins its own status`() {
        assertThat(NotFoundException("x").status).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(DuplicateException("x").status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(UnprocessableEntityException("x").status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(ConflictException("x").status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(ValidationException(listOf(FieldViolation("#/a", "a", "NotBlank", "must not be blank"))).status)
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        assertThat(SystemException("x").status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `details default to empty and are carried through unchanged`() {
        assertThat(NotFoundException("x").details).isEmpty()
        assertThat(NotFoundException("x", mapOf("reason" to "gone")).details).containsEntry("reason", "gone")
    }

    @Test
    fun `the base class refuses a non-client status and SystemException a non-server one`() {
        assertThatThrownBy { BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "x") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SystemException("x", status = HttpStatus.BAD_REQUEST) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a validation exception describes its violations and refuses an empty list`() {
        val one = FieldViolation("#/a", "a", "NotBlank", "must not be blank")
        assertThat(ValidationException(listOf(one)).message).isEqualTo("1 field is invalid: a")
        assertThat(ValidationException(listOf(one, one.copy(field = "b"))).message)
            .isEqualTo("2 fields are invalid: a, b")
        assertThatThrownBy { ValidationException(emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `the cause is preserved`() {
        val cause = IllegalStateException("constraint violated")
        assertThat(DuplicateException("x", emptyMap(), cause).cause).isSameAs(cause)
    }

    @ParameterizedTest
    @CsvSource(
        "400, bad-request",
        "401, unauthenticated",
        "403, access-denied",
        "404, not-found",
        "405, bad-request",
        "409, conflict",
        "413, bad-request",
        "422, unprocessable-entity",
        "500, internal-error",
        "502, internal-error",
        "503, service-unavailable",
    )
    fun `every status maps to one of the eight contract categories`(
        status: Int,
        category: String,
    ) {
        assertThat(ProblemCodes.forStatus(status)).isEqualTo(category)
    }
}
