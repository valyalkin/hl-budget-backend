package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.spending.SpendingRequest
import com.valyalkin.hlbudget.dto.spending.SpendingResponse
import com.valyalkin.hlbudget.dto.spending.toCommand
import com.valyalkin.hlbudget.dto.spending.toResponse
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.SpendingService
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/**
 * The caller's spendings (US3, US4). Returns ResponseEntity only on create, where the response
 * headers (`Location`, `Idempotent-Replayed`) are part of the contract.
 */
@RestController
@RequestMapping(SpendingController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_SPENDINGS)
class SpendingController(
    private val spendings: SpendingService,
) {
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createSpending",
        summary = "Record a spending",
        description =
            "Send an `Idempotency-Key` to make retries safe: repeating a create with the same key and the " +
                "same body returns the first spending with 200 and `Idempotent-Replayed: true` instead of " +
                "creating another. The key is freed when that spending is deleted.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        headers = [Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF)],
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = SpendingResponse::class),
                examples = [ExampleObject(name = "created", value = SPENDING_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(
        responseCode = "200",
        description = "A replay of an earlier create with the same Idempotency-Key and body; nothing new was created",
        headers = [
            Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF),
            Header(
                name = REPLAYED_HEADER,
                description = "Always `true` on a replay",
                schema = Schema(type = "string", allowableValues = ["true"]),
            ),
        ],
        content = [Content(mediaType = JSON, schema = Schema(implementation = SpendingResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.SPENDING_VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun createSpending(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(
            name = IDEMPOTENCY_HEADER,
            `in` = ParameterIn.HEADER,
            description = "Optional. 1 to 255 visible ASCII characters, unique per create you want deduplicated",
            example = "5d1f0c2e-retry-safe",
        )
        @RequestHeader(IDEMPOTENCY_HEADER, required = false)
        @Size(min = 1, max = MAX_KEY, message = "must be 1 to 255 characters")
        @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "must be visible ASCII characters only")
        idempotencyKey: String?,
        @Valid @RequestBody request: SpendingRequest,
    ): ResponseEntity<SpendingResponse> {
        val result = spendings.create(identity, request.toCommand(), idempotencyKey)
        val location = URI.create("$BASE_PATH/${result.spending.id}")
        val body = result.spending.toResponse()
        return if (result.replayed) {
            ResponseEntity
                .ok()
                .location(location)
                .header(REPLAYED_HEADER, "true")
                .body(body)
        } else {
            ResponseEntity.created(location).body(body)
        }
    }

    @GetMapping("/{spendingId}")
    @Operation(operationId = "getSpending", summary = "Fetch one of the caller's spendings")
    @ApiResponse(
        responseCode = "200",
        description = "The spending",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = SpendingResponse::class),
                examples = [ExampleObject(name = "found", value = SPENDING_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun getSpending(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable spendingId: UUID,
    ): SpendingResponse = spendings.get(identity, spendingId).toResponse()

    @PutMapping("/{spendingId}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "updateSpending", summary = "Replace all details of a spending")
    @ApiResponse(
        responseCode = "200",
        description = "Updated",
        content = [Content(mediaType = JSON, schema = Schema(implementation = SpendingResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.CONCURRENT_MODIFICATION)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.SPENDING_VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun updateSpending(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable spendingId: UUID,
        @Valid @RequestBody request: SpendingRequest,
    ): SpendingResponse = spendings.update(identity, spendingId, request.toCommand()).toResponse()

    @DeleteMapping("/{spendingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteSpending", summary = "Delete a spending permanently")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun deleteSpending(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable spendingId: UUID,
    ) = spendings.delete(identity, spendingId)

    companion object {
        const val BASE_PATH: String = "/api/v1/spendings"
        const val IDEMPOTENCY_HEADER: String = "Idempotency-Key"
        const val REPLAYED_HEADER: String = "Idempotent-Replayed"
        private const val MAX_KEY = 255
        private const val JSON = MediaType.APPLICATION_JSON_VALUE
        private const val UNAUTHENTICATED = "#/components/responses/Unauthenticated"
        private const val INTERNAL = "#/components/responses/InternalError"
        private const val UNAVAILABLE = "#/components/responses/ServiceUnavailable"
        private const val SPENDING_EXAMPLE =
            """{"id":"8d3a6b1f-4c2e-4f7a-9b0d-1e2c3a4b5c66","date":"2026-10-20","merchant":"Loblaws",""" +
                """"amount":{"amount":"84.37","currency":"CAD"},""" +
                """"category":{"id":"2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55","name":"Groceries"},""" +
                """"account":{"id":"3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11","name":"Amex"},""" +
                """"createdAt":"2026-10-27T14:03:11Z","updatedAt":"2026-10-27T14:03:11Z"}"""
    }
}
