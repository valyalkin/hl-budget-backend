package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.income.IncomePageResponse
import com.valyalkin.hlbudget.dto.income.IncomeRequest
import com.valyalkin.hlbudget.dto.income.IncomeResponse
import com.valyalkin.hlbudget.dto.income.toCommand
import com.valyalkin.hlbudget.dto.income.toResponse
import com.valyalkin.hlbudget.model.IncomeFilter
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.IncomeService
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
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate
import java.util.UUID

/**
 * The caller's income entries (feature 009, US1, US2), the twin of [SpendingController]. Returns
 * ResponseEntity only on create, where `Location` and `Idempotent-Replayed` are part of the contract.
 */
@RestController
@RequestMapping(IncomeController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_INCOMES)
class IncomeController(
    private val incomes: IncomeService,
) {
    @GetMapping
    @Operation(
        operationId = "listIncomes",
        summary = "List the caller's income entries, paged, filtered and sorted",
        description =
            "Filters are optional and combine with AND; repeating `categoryId` (income categories) or " +
                "`accountId` matches any of the given ids. Ties in the sort order are broken by creation time in " +
                "the same direction, then by id, so paging never skips or repeats an entry.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "One page; `items` is empty when nothing matches",
        content = [Content(mediaType = JSON, schema = Schema(implementation = IncomePageResponse::class))],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    @Suppress("LongParameterList")
    fun listIncomes(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Earliest date, inclusive", example = "2026-10-01")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @Parameter(description = "Latest date, inclusive; must not be before `from`", example = "2026-10-31")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
        @Parameter(description = "Repeatable; matches any of the given income categories")
        @RequestParam(name = "categoryId", required = false)
        categoryIds: List<UUID>?,
        @Parameter(description = "Repeatable; matches any of the given accounts")
        @RequestParam(name = "accountId", required = false)
        accountIds: List<UUID>?,
        @Parameter(description = "0-based page number")
        @RequestParam(defaultValue = "0")
        @Min(0, message = "must be 0 or more")
        page: Int,
        @Parameter(description = "Items per page, 1 to 100")
        @RequestParam(defaultValue = "20")
        @Min(1, message = "must be between 1 and 100")
        @Max(MAX_PAGE_SIZE, message = "must be between 1 and 100")
        size: Int,
        @Parameter(
            description = "Sort order",
            schema =
                Schema(
                    type = "string",
                    allowableValues = ["date,desc", "date,asc", "amount,desc", "amount,asc"],
                    defaultValue = "date,desc",
                ),
        )
        @RequestParam(required = false)
        sort: String?,
    ): IncomePageResponse =
        incomes
            .list(
                identity,
                IncomeFilter(from, to, categoryIds.orEmpty().toSet(), accountIds.orEmpty().toSet(), sort),
                page,
                size,
            ).toResponse()

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "createIncome",
        summary = "Record an income entry",
        description =
            "Send an `Idempotency-Key` to make retries safe: repeating a create with the same key and the " +
                "same body returns the first entry with 200 and `Idempotent-Replayed: true` instead of " +
                "creating another. The key is freed when that entry is deleted. " + BY_NAME,
    )
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        headers = [Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF)],
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = IncomeResponse::class),
                examples = [ExampleObject(name = "created", value = INCOME_EXAMPLE)],
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
        content = [Content(mediaType = JSON, schema = Schema(implementation = IncomeResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.INCOME_VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun createIncome(
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
        @Valid @RequestBody request: IncomeRequest,
    ): ResponseEntity<IncomeResponse> {
        val result = incomes.create(identity, request.toCommand(), idempotencyKey)
        val location = URI.create("$BASE_PATH/${result.income.id}")
        val body = result.income.toResponse()
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

    @GetMapping("/{incomeId}")
    @Operation(operationId = "getIncome", summary = "Fetch one of the caller's income entries")
    @ApiResponse(
        responseCode = "200",
        description = "The income entry",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = IncomeResponse::class),
                examples = [ExampleObject(name = "found", value = INCOME_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun getIncome(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable incomeId: UUID,
    ): IncomeResponse = incomes.get(identity, incomeId).toResponse()

    @PutMapping("/{incomeId}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "updateIncome",
        summary = "Replace all details of an income entry",
        description = BY_NAME,
    )
    @ApiResponse(
        responseCode = "200",
        description = "Updated",
        content = [Content(mediaType = JSON, schema = Schema(implementation = IncomeResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.CONCURRENT_MODIFICATION)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.INCOME_VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun updateIncome(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable incomeId: UUID,
        @Valid @RequestBody request: IncomeRequest,
    ): IncomeResponse = incomes.update(identity, incomeId, request.toCommand()).toResponse()

    @DeleteMapping("/{incomeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteIncome", summary = "Delete an income entry permanently")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun deleteIncome(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable incomeId: UUID,
    ) = incomes.delete(identity, incomeId)

    companion object {
        const val BASE_PATH: String = "/api/v1/incomes"
        private const val IDEMPOTENCY_HEADER = SpendingController.IDEMPOTENCY_HEADER
        private const val REPLAYED_HEADER = SpendingController.REPLAYED_HEADER
        private const val MAX_KEY = 255
        private const val MAX_PAGE_SIZE = 100L
        private const val JSON = MediaType.APPLICATION_JSON_VALUE
        private const val UNAUTHENTICATED = "#/components/responses/Unauthenticated"
        private const val INTERNAL = "#/components/responses/InternalError"
        private const val UNAVAILABLE = "#/components/responses/ServiceUnavailable"
        private const val BY_NAME =
            "The income category and the account are each given by id or by name (exactly one of each pair). " +
                "A name is matched against the caller's income categories or accounts ignoring case and " +
                "surrounding spaces; a name that matches none, including a spending category's name, is refused " +
                "with NotFound and nothing is created."
        private const val INCOME_EXAMPLE =
            """{"id":"9b1e2c3d-4f5a-4b6c-8d7e-0f1a2b3c4d5e","date":"2026-10-15","source":"Acme Corp",""" +
                """"amount":{"amount":"5000.00","currency":"CAD"},""" +
                """"category":{"id":"6e7f8091-2b3c-4d4e-8f5a-6b7c8d9e0f12","name":"Salary"},""" +
                """"account":{"id":"3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11","name":"Debit card"},""" +
                """"createdAt":"2026-10-15T14:03:11Z","updatedAt":"2026-10-15T14:03:11Z"}"""
    }
}
