package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports the running document to contracts/api.yaml (the file the frontend generates its types
 * from) and asserts it carries the operation, every outcome and the profile schema.
 */
class OpenApiExportIT : AbstractIntegrationTest() {
    @Test
    fun `the document is exported to contracts api yaml`() {
        val yaml =
            client
                .get()
                .uri("/api/v3/api-docs.yaml")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

        requireNotNull(yaml) { "the OpenAPI document was empty" }
        val target = Path.of("contracts", "api.yaml")
        Files.createDirectories(target.parent)
        Files.writeString(target, yaml)

        assertThat(yaml).contains("openapi: 3.1.0", "/api/v1/users/me", "ensureMyProfile")
    }

    @Test
    fun `PUT users me documents every outcome and the UserProfile fields`() {
        val operation = "$.paths['/api/v1/users/me'].put"
        client
            .get()
            .uri("/api/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$operation.operationId")
            .isEqualTo("ensureMyProfile")
            .jsonPath("$operation.responses.200")
            .exists()
            .jsonPath("$operation.responses.201.headers.Location")
            .exists()
            .jsonPath("$operation.responses.401")
            .exists()
            .jsonPath("$operation.responses.500")
            .exists()
            .jsonPath("$operation.responses.503")
            .exists()
            .jsonPath("$operation.parameters")
            .doesNotExist()
            .jsonPath("$operation.requestBody")
            .doesNotExist()
            .jsonPath("$.components.schemas.UserProfile.required.length()")
            .isEqualTo(6)
            .jsonPath("$.components.schemas.UserProfile.properties.auth0Subject")
            .doesNotExist()
            .jsonPath("$.components.schemas.Problem.properties.traceId")
            .exists()
            .jsonPath("$.components.securitySchemes.bearerAuth.scheme")
            .isEqualTo("bearer")
            .jsonPath("$.paths['/healthz'].get.operationId")
            .isEqualTo("liveness")
            .jsonPath("$.paths['/readyz'].get.responses.503")
            .exists()
            // Only the contract's paths: no framework endpoints, no Spring ProblemDetail schema.
            .jsonPath("$.paths.length()")
            .isEqualTo(3)
            .jsonPath("$.components.schemas.ProblemDetail")
            .doesNotExist()
    }
}
