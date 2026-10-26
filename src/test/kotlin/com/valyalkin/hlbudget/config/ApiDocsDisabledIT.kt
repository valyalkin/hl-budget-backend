package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource

/**
 * The cluster sets SPRINGDOC_API_DOCS_ENABLED and SPRINGDOC_SWAGGER_UI_ENABLED to false, so the
 * document is not public on the shared host. Its paths stay permitted by SecurityConfig, so the
 * result must be the ordinary 404 problem, not a 401 or a framework error page.
 */
@TestPropertySource(properties = ["springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"])
class ApiDocsDisabledIT : AbstractIntegrationTest() {
    @Test
    fun `with springdoc disabled the document and Swagger UI are a 404 problem`() {
        listOf("/api/v3/api-docs", "/api/v3/api-docs.yaml", "/api/swagger-ui.html").forEach { path ->
            val body =
                client
                    .get()
                    .uri(path)
                    .exchange()
                    .expectStatus()
                    .isNotFound
                    .expectHeader()
                    .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody(String::class.java)
                    .returnResult()
                    .responseBody
                    .orEmpty()
            assertThat(body).describedAs(path).contains("\"traceId\"").doesNotContain("openapi")
        }
    }
}
