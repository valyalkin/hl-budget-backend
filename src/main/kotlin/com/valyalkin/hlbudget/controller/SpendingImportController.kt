package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.spendingimport.SpendingImportRequest
import com.valyalkin.hlbudget.dto.spendingimport.SpendingImportResponse
import com.valyalkin.hlbudget.dto.spendingimport.toCommands
import com.valyalkin.hlbudget.dto.spendingimport.toResponse
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.SpendingImportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as RequestBodyDoc

/**
 * Many spendings at once, all or nothing (feature 004, research R-006). Create returns
 * ResponseEntity because `Location` and `Idempotent-Replayed` are part of the contract.
 */
@RestController
@RequestMapping(SpendingImportController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_SPENDING_IMPORTS)
class SpendingImportController(
    private val imports: SpendingImportService,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createSpendingImport",
        summary = "Record many spendings at once, all or nothing",
        description =
            "Creates every row as a spending in one step. If any row is invalid, nothing is created and every " +
                "problem is listed, with a JSON Pointer naming the row (`#/spendings/3/date`).\n\n" +
                "Categories and accounts are given by name. Each name is matched against the caller's items " +
                "ignoring case and surrounding spaces, and created when none matches. A name that appears in " +
                "several rows, in any letter case, creates one item.\n\n" +
                "Send an `Idempotency-Key` to make retries safe: repeating an import with the same key and the " +
                "same rows returns the first import with 200 and `Idempotent-Replayed: true` instead of " +
                "creating the spendings again.",
        requestBody =
            RequestBodyDoc(
                required = true,
                content = [
                    Content(
                        mediaType = JSON,
                        schema = Schema(implementation = SpendingImportRequest::class),
                        examples = [ExampleObject(name = "twoRows", value = REQUEST_EXAMPLE)],
                    ),
                ],
            ),
    )
    @ApiResponse(
        responseCode = "201",
        description = "Created; every row is now a spending",
        headers = [Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF)],
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = SpendingImportResponse::class),
                examples = [ExampleObject(name = "created", value = IMPORT_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(
        responseCode = "200",
        description = "A replay of an earlier import with the same Idempotency-Key and rows; nothing new was created",
        headers = [
            Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF),
            Header(
                name = SpendingController.REPLAYED_HEADER,
                description = "Always `true` on a replay",
                schema = Schema(type = "string", allowableValues = ["true"]),
            ),
        ],
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = SpendingImportResponse::class),
                examples = [ExampleObject(name = "replayed", value = IMPORT_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.SPENDING_IMPORT_VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun createSpendingImport(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(
            name = SpendingController.IDEMPOTENCY_HEADER,
            `in` = ParameterIn.HEADER,
            description = "Optional. 1 to 255 visible ASCII characters; a UUID is recommended",
            example = "6a1d9c2e-8f4b-4b7a-9e3c-1f2d3c4b5a69",
        )
        @RequestHeader(SpendingController.IDEMPOTENCY_HEADER, required = false)
        @Size(min = 1, max = MAX_KEY, message = "must be 1 to 255 characters")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "must be visible ASCII characters only")
        idempotencyKey: String?,
        @Valid @RequestBody request: SpendingImportRequest,
    ): ResponseEntity<SpendingImportResponse> {
        val result = imports.create(identity, request.toCommands(), idempotencyKey)
        val location = URI.create("$BASE_PATH/${result.import.id}")
        val body = result.import.toResponse()
        return if (result.replayed) {
            ResponseEntity
                .ok()
                .location(location)
                .header(SpendingController.REPLAYED_HEADER, "true")
                .body(body)
        } else {
            ResponseEntity.created(location).body(body)
        }
    }

    @GetMapping("/{importId}")
    @Operation(
        operationId = "getSpendingImport",
        summary = "One of the caller's imports",
        description =
            "The summary recorded when the import was created. It does not change when the imported spendings " +
                "are later edited or deleted. Another user's import is 404, like an unknown id.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "The import",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = SpendingImportResponse::class),
                examples = [ExampleObject(name = "found", value = IMPORT_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun getSpendingImport(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(example = "9b2f6e1a-4c3d-4e5f-8a7b-0c1d2e3f4a5b")
        @PathVariable importId: UUID,
    ): SpendingImportResponse = imports.get(identity, importId).toResponse()

    companion object {
        const val BASE_PATH: String = "/api/v1/spending-imports"
        private const val MAX_KEY = 255
        private const val JSON = MediaType.APPLICATION_JSON_VALUE
        private const val UNAUTHENTICATED = "#/components/responses/Unauthenticated"
        private const val INTERNAL = "#/components/responses/InternalError"
        private const val UNAVAILABLE = "#/components/responses/ServiceUnavailable"
        private const val REQUEST_EXAMPLE =
            """{"spendings":[""" +
                """{"date":"2026-10-28","merchant":"Corner Bakery","amount":{"amount":"12.40","currency":"CAD"},""" +
                """"categoryName":"Groceries","accountName":"Visa"},""" +
                """{"date":"2026-09-30","merchant":"Lakeshore Fuel, Hwy 7",""" +
                """"amount":{"amount":"61.20","currency":"CAD"},"categoryName":"car","accountName":"Amex"}]}"""
        private const val IMPORT_EXAMPLE =
            """{"id":"9b2f6e1a-4c3d-4e5f-8a7b-0c1d2e3f4a5b","spendingCount":2,"firstDate":"2026-09-30",""" +
                """"lastDate":"2026-10-28","total":{"amount":"73.60","currency":"CAD"},""" +
                """"createdAt":"2026-10-29T14:03:11Z"}"""
    }
}
