package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.income.IncomeSourceListResponse
import com.valyalkin.hlbudget.dto.income.toIncomeSourceResponse
import com.valyalkin.hlbudget.model.Ledger
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.MerchantService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The caller's past income sources, for autofill (feature 009, FR-063): the twin of /merchants. */
@RestController
@Tag(name = OpenApiConfig.TAG_INCOMES)
class IncomeSourceController(
    private val merchants: MerchantService,
) {
    @GetMapping("/api/v1/income-sources", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listIncomeSources",
        summary = "The caller's past income sources, most used first, for autofill",
        description =
            "Sources are derived from income entries only (never from spendings): entries whose source is " +
                "equal ignoring letter case and surrounding spaces count as one. `name`, `lastCategory` and " +
                "`lastAccount` come from its most recent entry. Ordered by `incomeCount` descending, then name.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Sources; `truncated` is true when more exist than `limit`",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = IncomeSourceListResponse::class),
                examples = [ExampleObject(name = "sources", value = EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun listIncomeSources(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Maximum number of sources returned, 1 to 1000")
        @RequestParam(defaultValue = "500")
        @Min(1, message = "must be between 1 and 1000")
        @Max(MAX_LIMIT, message = "must be between 1 and 1000")
        limit: Int,
    ): IncomeSourceListResponse = merchants.mostUsed(identity, limit, Ledger.INCOMES).toIncomeSourceResponse()

    private companion object {
        const val MAX_LIMIT = 1000L
        const val EXAMPLE =
            """{"truncated":false,"items":[{"name":"Acme Corp","incomeCount":10,""" +
                """"lastUsedOn":"2026-10-15",""" +
                """"lastCategory":{"id":"6e7f8091-2b3c-4d4e-8f5a-6b7c8d9e0f12","name":"Salary"},""" +
                """"lastAccount":{"id":"3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11","name":"Debit card"}}]}"""
    }
}
