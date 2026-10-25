package com.valyalkin.hlbudget.error

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.constraints.NotBlank
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/** Handler-level coverage of mappings that are awkward to provoke over HTTP. */
class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler(ProblemDetailFactory(), ViolationExtractor())
    private val request = MockHttpServletRequest("PUT", "/api/v1/users/me")

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun ProblemDetail.errors(): List<*> = properties?.get("errors") as? List<*> ?: emptyList<Any>()

    @Test
    fun `a business exception publishes its message and details`() {
        val problem =
            handler.handleBusiness(
                NotFoundException("User was not found", mapOf("reason" to "gone")),
                request,
            )

        assertThat(problem.status).isEqualTo(404)
        assertThat(problem.type.toString()).isEqualTo("urn:problem:hl-budget:not-found")
        assertThat(problem.detail).isEqualTo("User was not found")
        assertThat(problem.properties).containsKey("details")
    }

    @Test
    fun `an empty details map adds nothing`() {
        assertThat(handler.handleBusiness(NotFoundException("gone"), request).properties).doesNotContainKey("details")
    }

    @Test
    fun `a system exception reports its status but never its message or details`() {
        val problem =
            handler.handleSystem(
                SystemException("connection refused to db:5432", mapOf("host" to "db"), HttpStatus.SERVICE_UNAVAILABLE),
                request,
            )

        assertThat(problem.status).isEqualTo(503)
        assertThat(problem.type.toString()).isEqualTo("urn:problem:hl-budget:service-unavailable")
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.UNAVAILABLE_DETAIL)
        assertThat(problem.properties).doesNotContainKey("details")
    }

    @Test
    fun `an unexpected exception is a 500 with the fixed detail`() {
        val problem = handler.handleUnexpected(IllegalStateException("column users.secret missing"), request)

        assertThat(problem.status).isEqualTo(500)
        assertThat(problem.detail).isEqualTo("An unexpected error occurred")
    }

    @Test
    fun `a constraint violation is reported field by field`() {
        data class Payload(
            @field:NotBlank val reference: String,
        )
        val validator = Validation.buildDefaultValidatorFactory().validator
        val problem =
            handler.handleConstraintViolation(
                ConstraintViolationException(validator.validate(Payload(""))),
                request,
            )

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.type.toString()).isEqualTo("urn:problem:hl-budget:bad-request")
        assertThat(problem.detail).isEqualTo("1 field is invalid: reference")
        assertThat(
            problem.errors(),
        ).singleElement().isEqualTo(FieldViolation("#/reference", "reference", "NotBlank", "must not be blank"))
    }

    @Test
    fun `no extractable violations still reads sensibly`() {
        assertThat(handler.handleConstraintViolation(ConstraintViolationException(emptySet()), request).detail)
            .isEqualTo("The request is invalid.")
    }

    @Test
    fun `an unparseable body is a 400 that does not echo the parser message`() {
        val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
        val parseFailure = runCatching { mapper.readValue("{", Map::class.java) }.exceptionOrNull()
        val exception =
            HttpMessageNotReadableException(
                "JSON parse error at [Source: x]",
                parseFailure,
                MockHttpInputMessage("{".toByteArray()),
            )

        val problem = handler.handleUnreadableBody(exception, request)

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.detail).isEqualTo("The request body is not valid JSON.")
    }

    @Test
    fun `a data integrity violation is a 409 that keeps the constraint name out`() {
        val problem = handler.handleDataIntegrity(DataIntegrityViolationException("uq_users_auth0_subject"), request)

        assertThat(problem.status).isEqualTo(409)
        assertThat(problem.detail).doesNotContain("uq_users_auth0_subject")
    }

    @Test
    fun `an optimistic lock failure is a conflict with a reason`() {
        val problem = handler.handleOptimisticLocking(OptimisticLockingFailureException("row changed"), request)

        assertThat(problem.status).isEqualTo(409)
        assertThat(problem.type.toString()).isEqualTo("urn:problem:hl-budget:conflict")
    }

    @Test
    fun `access denied is 401 when anonymous and 403 when authenticated`() {
        SecurityContextHolder.getContext().authentication =
            AnonymousAuthenticationToken("key", "anonymous", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        assertThat(handler.handleAccessDenied(AccessDeniedException("denied"), request).status).isEqualTo(401)

        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("user", "token", "SCOPE_x")
        assertThat(handler.handleAccessDenied(AccessDeniedException("denied"), request).status).isEqualTo(403)
    }

    @Test
    fun `an unknown path is a 404 naming the method and path`() {
        val problem =
            handler.handleNoResource(
                NoResourceFoundException(HttpMethod.GET, "/api/v1/nope", "nope"),
                MockHttpServletRequest("GET", "/api/v1/nope"),
            )

        assertThat(problem.status).isEqualTo(404)
        assertThat(problem.detail).contains("GET", "/api/v1/nope")
    }

    @Test
    fun `protocol failures keep their status under the bad-request category`() {
        val methods = handler.handleMethodNotSupported(HttpRequestMethodNotSupportedException("TRACE"), request)
        val media =
            handler.handleMediaTypeNotSupported(
                HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, listOf(MediaType.APPLICATION_JSON)),
                request,
            )
        val accept =
            handler.handleNotAcceptable(
                HttpMediaTypeNotAcceptableException(listOf(MediaType.APPLICATION_JSON)),
                request,
            )

        assertThat(listOf(methods.status, media.status, accept.status)).containsExactly(405, 415, 406)
        assertThat(methods.detail).doesNotContain("null")
        assertThat(media.type.toString()).isEqualTo("urn:problem:hl-budget:bad-request")
    }
}
