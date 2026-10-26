package com.valyalkin.hlbudget.error

import jakarta.servlet.RequestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.webmvc.error.DefaultErrorAttributes
import org.springframework.mock.web.MockHttpServletRequest

class ProblemErrorControllerTest {
    private val controller = ProblemErrorController(ProblemDetailFactory(), DefaultErrorAttributes())

    private fun dispatch(status: Int?) =
        MockHttpServletRequest("GET", "/error").apply {
            setAttribute(RequestId.ATTRIBUTE, "trace-9")
            status?.let { setAttribute(RequestDispatcher.ERROR_STATUS_CODE, it) }
            setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/users/me")
        }

    @ParameterizedTest
    @CsvSource(
        "404, urn:problem:hl-budget:not-found",
        "401, urn:problem:hl-budget:unauthenticated",
        "403, urn:problem:hl-budget:access-denied",
        "400, urn:problem:hl-budget:bad-request",
        "418, urn:problem:hl-budget:bad-request",
    )
    fun `a client-side dispatch keeps the status and maps to a contract type`(
        status: Int,
        expectedType: String,
    ) {
        val problem = controller.handleError(dispatch(status))

        assertThat(problem.status).isEqualTo(status)
        assertThat(problem.type.toString()).isEqualTo(expectedType)
        assertThat(problem.properties).containsEntry("traceId", "trace-9")
    }

    @Test
    fun `a server-side dispatch says nothing about the cause`() {
        val problem = controller.handleError(dispatch(500))

        assertThat(problem.type.toString()).isEqualTo("urn:problem:hl-budget:internal-error")
        assertThat(problem.detail).isEqualTo(ProblemDetailFactory.SAFE_SERVER_DETAIL)
    }

    @Test
    fun `a 503 dispatch uses the unavailable detail`() {
        assertThat(controller.handleError(dispatch(503)).detail).isEqualTo(ProblemDetailFactory.UNAVAILABLE_DETAIL)
    }

    @Test
    fun `an absent or nonsensical status falls back to 500`() {
        assertThat(controller.handleError(dispatch(null)).status).isEqualTo(500)
        assertThat(controller.handleError(dispatch(7)).status).isEqualTo(500)
    }
}
