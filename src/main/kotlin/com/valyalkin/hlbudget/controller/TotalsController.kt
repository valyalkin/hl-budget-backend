package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.totals.CategoryTotalsResponse
import com.valyalkin.hlbudget.dto.totals.MonthlyTotalsResponse
import com.valyalkin.hlbudget.dto.totals.toResponse
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.TotalsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/** Sums of the caller's spendings by month and by category (feature 003, research R-002). */
@RestController
@Tag(name = OpenApiConfig.TAG_TOTALS)
class TotalsController(
    private val totals: TotalsService,
) {
    @GetMapping("/api/v1/monthly-totals", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listMonthlyTotals",
        summary = "The caller's spending total and count for each month of a year",
        description =
            "Always 12 entries, January to December; a month with no spendings has a zero total and count. " +
                "`today` is the current date in the app's time zone, so clients can tell which months are in " +
                "the future without a clock of their own.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Twelve months",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = MonthlyTotalsResponse::class),
                examples = [ExampleObject(name = "year", value = MONTHLY_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun listMonthlyTotals(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Calendar year, 2000 to 2100; defaults to the current year", example = "2026")
        @RequestParam(required = false)
        @Min(MIN_YEAR, message = "must be between 2000 and 2100")
        @Max(MAX_YEAR, message = "must be between 2000 and 2100")
        year: Int?,
    ): MonthlyTotalsResponse = totals.monthly(identity, year).toResponse()

    @GetMapping("/api/v1/category-totals", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listCategoryTotals",
        summary = "The caller's spending per category over a date range",
        description =
            "One entry per category with at least one spending in the range, and only those; largest total " +
                "first, ties by category name. Not paged: bounded by the caller's category count.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Totals per category; `items` is empty when nothing was spent",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = CategoryTotalsResponse::class),
                examples = [ExampleObject(name = "october", value = CATEGORY_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun listCategoryTotals(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Earliest date, inclusive", example = "2026-10-01")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate,
        @Parameter(
            description = "Latest date, inclusive; not before `from`, at most 366 days after it",
            example = "2026-10-31",
        )
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate,
    ): CategoryTotalsResponse = totals.byCategory(identity, from, to).toResponse()

    private companion object {
        const val MIN_YEAR = 2000L
        const val MAX_YEAR = 2100L
        const val JSON = MediaType.APPLICATION_JSON_VALUE
        const val UNAUTHENTICATED = "#/components/responses/Unauthenticated"
        const val INTERNAL = "#/components/responses/InternalError"
        const val UNAVAILABLE = "#/components/responses/ServiceUnavailable"
        const val MONTHLY_EXAMPLE =
            """{"year":2026,"today":"2026-10-28","months":[""" +
                """{"month":"2026-01","total":{"amount":"0.00","currency":"CAD"},"spendingCount":0},""" +
                """{"month":"2026-09","total":{"amount":"2741.10","currency":"CAD"},"spendingCount":22},""" +
                """{"month":"2026-10","total":{"amount":"2748.42","currency":"CAD"},"spendingCount":14}]}"""
        const val CATEGORY_EXAMPLE =
            """{"from":"2026-10-01","to":"2026-10-31","total":{"amount":"2326.44","currency":"CAD"},""" +
                """"spendingCount":3,"items":[""" +
                """{"category":{"id":"5b1e2c3d-4f5a-4b6c-8d7e-9f0a1b2c3d44","name":"Rent"},""" +
                """"total":{"amount":"2150.00","currency":"CAD"},"spendingCount":1},""" +
                """{"category":{"id":"2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55","name":"Groceries"},""" +
                """"total":{"amount":"176.44","currency":"CAD"},"spendingCount":2}]}"""
    }
}
