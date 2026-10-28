package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.merchant.MerchantListResponse
import com.valyalkin.hlbudget.dto.merchant.toResponse
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

/** The caller's past merchants, for autofill (feature 003, research R-005). */
@RestController
@Tag(name = OpenApiConfig.TAG_MERCHANTS)
class MerchantController(
    private val merchants: MerchantService,
) {
    @GetMapping("/api/v1/merchants", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listMerchants",
        summary = "The caller's past merchants, most used first, for autofill",
        description =
            "Merchants are derived from spendings, not stored: spendings whose merchant is equal ignoring " +
                "letter case and surrounding spaces count as one merchant. `name`, `lastCategory` and " +
                "`lastAccount` come from that merchant's most recent spending. Ordered by `spendingCount` " +
                "descending, then name.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Merchants; `truncated` is true when more exist than `limit`",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = MerchantListResponse::class),
                examples = [ExampleObject(name = "merchants", value = EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun listMerchants(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Maximum number of merchants returned, 1 to 1000")
        @RequestParam(defaultValue = "500")
        @Min(1, message = "must be between 1 and 1000")
        @Max(MAX_LIMIT, message = "must be between 1 and 1000")
        limit: Int,
    ): MerchantListResponse = merchants.mostUsed(identity, limit).toResponse()

    private companion object {
        const val MAX_LIMIT = 1000L
        const val EXAMPLE =
            """{"truncated":false,"items":[{"name":"Neighbourhood Market","spendingCount":12,""" +
                """"lastUsedOn":"2026-10-27",""" +
                """"lastCategory":{"id":"2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55","name":"Groceries"},""" +
                """"lastAccount":{"id":"3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11","name":"Visa"}}]}"""
    }
}
