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
            .isEqualTo(14)
            .jsonPath("$.paths['/api/v1/spending-imports'].post.responses.201")
            .exists()
            .jsonPath("$.paths['/api/v1/spending-imports'].post.responses.422")
            .exists()
            .jsonPath("$.paths['/api/v1/spending-imports/{importId}'].get.responses.404")
            .exists()
            .jsonPath("$.components.responses.SpendingImportValidationFailed")
            .exists()
            .jsonPath("$.components.schemas.ProblemDetail")
            .doesNotExist()
    }

    private val operations =
        mapOf(
            "get /api/v1/accounts" to ("listAccounts" to listOf(200, 401, 500, 503)),
            "post /api/v1/accounts" to ("createAccount" to listOf(201, 400, 401, 409, 422, 500, 503)),
            "get /api/v1/accounts/{accountId}" to ("getAccount" to listOf(200, 400, 401, 404, 500, 503)),
            "put /api/v1/accounts/{accountId}" to
                ("renameAccount" to listOf(200, 400, 401, 404, 409, 422, 500, 503)),
            "delete /api/v1/accounts/{accountId}" to
                ("deleteAccount" to listOf(204, 400, 401, 404, 409, 422, 500, 503)),
            "get /api/v1/categories" to ("listCategories" to listOf(200, 401, 500, 503)),
            "post /api/v1/categories" to ("createCategory" to listOf(201, 400, 401, 409, 422, 500, 503)),
            "get /api/v1/categories/{categoryId}" to ("getCategory" to listOf(200, 400, 401, 404, 500, 503)),
            "put /api/v1/categories/{categoryId}" to
                ("renameCategory" to listOf(200, 400, 401, 404, 409, 422, 500, 503)),
            "delete /api/v1/categories/{categoryId}" to
                ("deleteCategory" to listOf(204, 400, 401, 404, 409, 422, 500, 503)),
            "get /api/v1/spendings" to ("listSpendings" to listOf(200, 401, 422, 500, 503)),
            "post /api/v1/spendings" to ("createSpending" to listOf(200, 201, 400, 401, 422, 500, 503)),
            "get /api/v1/spendings/{spendingId}" to ("getSpending" to listOf(200, 400, 401, 404, 500, 503)),
            "put /api/v1/spendings/{spendingId}" to
                ("updateSpending" to listOf(200, 400, 401, 404, 409, 422, 500, 503)),
            "delete /api/v1/spendings/{spendingId}" to
                ("deleteSpending" to listOf(204, 400, 401, 404, 500, 503)),
            "get /api/v1/monthly-totals" to ("listMonthlyTotals" to listOf(200, 401, 422, 500, 503)),
            "get /api/v1/category-totals" to ("listCategoryTotals" to listOf(200, 401, 422, 500, 503)),
            "get /api/v1/merchants" to ("listMerchants" to listOf(200, 401, 422, 500, 503)),
        )

    @Test
    fun `every feature 002 and 003 operation documents exactly its outcomes`() {
        var document = emptyMap<String, Any>()
        client
            .get()
            .uri("/api/v3/api-docs")
            .exchange()
            .expectBody()
            .jsonPath("$")
            .value<Map<String, Any>> { document = it }

        @Suppress("UNCHECKED_CAST")
        val paths = document.getValue("paths") as Map<String, Map<String, Map<String, Any>>>
        operations.forEach { (key, expected) ->
            val (method, path) = key.split(' ')
            val operation = paths.getValue(path).getValue(method)
            assertThat(operation["operationId"]).describedAs(key).isEqualTo(expected.first)

            @Suppress("UNCHECKED_CAST")
            val codes = (operation["responses"] as Map<String, Any>).keys.map(String::toInt)
            assertThat(codes).describedAs(key).containsExactlyInAnyOrderElementsOf(expected.second)
        }
    }

    @Test
    fun `money is a decimal string, and the idempotency header and error examples are documented`() {
        val create = "$.paths['/api/v1/spendings'].post"
        client
            .get()
            .uri("/api/v3/api-docs")
            .exchange()
            .expectBody()
            .jsonPath("$.components.schemas.Money.properties.amount.type")
            .isEqualTo("string")
            .jsonPath("$.components.schemas.Money.properties.amount.pattern")
            .isEqualTo("^\\d{1,8}(\\.\\d{1,2})?$")
            .jsonPath("$create.parameters[?(@.name == 'Idempotency-Key')].in")
            .isEqualTo("header")
            .jsonPath("$create.responses.200.headers.Idempotent-Replayed")
            .exists()
            .jsonPath("$.components.schemas.Category.properties.isDefault")
            .exists()
            .jsonPath("$.components.schemas.SpendingPage.required.length()")
            .isEqualTo(5)
            .jsonPath("$.components.schemas.Spending.required.length()")
            .isEqualTo(8)
            .jsonPath("$.components.schemas.Category.required.length()")
            .isEqualTo(4)
            .jsonPath("$.components.responses.InUse.content['application/problem+json'].examples.inUse")
            .exists()
            .jsonPath("$.components.responses.SpendingValidationFailed.content['application/problem+json'].examples")
            .exists()
    }
}
