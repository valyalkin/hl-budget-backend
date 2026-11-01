package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.totals.IncomeAccountTotalsResponse
import com.valyalkin.hlbudget.dto.totals.IncomeCategoryTotalsResponse
import com.valyalkin.hlbudget.dto.totals.IncomeMonthlyTotalsResponse
import com.valyalkin.hlbudget.dto.totals.toIncomeResponse
import com.valyalkin.hlbudget.model.Ledger
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

/**
 * Sums of the caller's income by month, by income category and by account (feature 009, research
 * R-003): the twins of the spending totals in [TotalsController], with the same rules.
 */
@RestController
@Tag(name = OpenApiConfig.TAG_TOTALS)
class IncomeTotalsController(
    private val totals: TotalsService,
) {
    @GetMapping("/api/v1/monthly-income-totals", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listMonthlyIncomeTotals",
        summary = "The caller's income total and entry count for each month of a year",
        description =
            "Always 12 entries, January to December; a month with no income has a zero total and count. " +
                "`today` is the current date in the app's time zone. Spendings are never included.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Twelve months",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = IncomeMonthlyTotalsResponse::class),
                examples = [ExampleObject(name = "year", value = MONTHLY_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun listMonthlyIncomeTotals(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Calendar year, 2000 to 2100; defaults to the current year", example = "2026")
        @RequestParam(required = false)
        @Min(MIN_YEAR, message = "must be between 2000 and 2100")
        @Max(MAX_YEAR, message = "must be between 2000 and 2100")
        year: Int?,
    ): IncomeMonthlyTotalsResponse = totals.monthly(identity, year, Ledger.INCOMES).toIncomeResponse()

    @GetMapping("/api/v1/income-category-totals", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listIncomeCategoryTotals",
        summary = "The caller's income per income category over a date range",
        description =
            "One entry per income category with income in the range, and only those; largest total first, " +
                "ties by name. The same range rules as /api/v1/category-totals. Not paged: bounded by the " +
                "caller's income category count.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Totals per income category; `items` is empty when nothing was received",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = IncomeCategoryTotalsResponse::class),
                examples = [ExampleObject(name = "october", value = CATEGORY_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun listIncomeCategoryTotals(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Earliest date, inclusive", example = "2026-10-01")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate,
        @Parameter(description = RANGE_TO, example = "2026-10-31")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate,
    ): IncomeCategoryTotalsResponse = totals.byCategory(identity, from, to, Ledger.INCOMES).toIncomeResponse()

    @GetMapping("/api/v1/income-account-totals", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listIncomeAccountTotals",
        summary = "The caller's income per account over a date range",
        description =
            "One entry per account that received income in the range, and only those; largest total first, " +
                "ties by name, each with the account's colour. The same range rules as /api/v1/account-totals. " +
                "Not paged: bounded by the caller's account count.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Totals per account; `items` is empty when nothing was received",
        content = [
            Content(
                mediaType = JSON,
                schema = Schema(implementation = IncomeAccountTotalsResponse::class),
                examples = [ExampleObject(name = "october", value = ACCOUNT_EXAMPLE)],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun listIncomeAccountTotals(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Parameter(description = "Earliest date, inclusive", example = "2026-10-01")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate,
        @Parameter(description = RANGE_TO, example = "2026-10-31")
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate,
    ): IncomeAccountTotalsResponse = totals.byAccount(identity, from, to, Ledger.INCOMES).toIncomeResponse()

    private companion object {
        const val MIN_YEAR = 2000L
        const val MAX_YEAR = 2100L
        const val JSON = MediaType.APPLICATION_JSON_VALUE
        const val UNAUTHENTICATED = "#/components/responses/Unauthenticated"
        const val INTERNAL = "#/components/responses/InternalError"
        const val UNAVAILABLE = "#/components/responses/ServiceUnavailable"
        const val RANGE_TO = "Latest date, inclusive; not before `from`, at most 366 days after it"
        const val MONTHLY_EXAMPLE =
            """{"year":2026,"today":"2026-10-28","months":[""" +
                """{"month":"2026-01","total":{"amount":"0.00","currency":"CAD"},"entryCount":0},""" +
                """{"month":"2026-09","total":{"amount":"5000.00","currency":"CAD"},"entryCount":1},""" +
                """{"month":"2026-10","total":{"amount":"5320.50","currency":"CAD"},"entryCount":3}]}"""
        const val CATEGORY_EXAMPLE =
            """{"from":"2026-10-01","to":"2026-10-31","total":{"amount":"5320.50","currency":"CAD"},""" +
                """"entryCount":3,"items":[""" +
                """{"category":{"id":"6e7f8091-2b3c-4d4e-8f5a-6b7c8d9e0f12","name":"Salary"},""" +
                """"total":{"amount":"5000.00","currency":"CAD"},"entryCount":1,"color":"#1e5b45"},""" +
                """{"category":{"id":"5d6e7f80-1a2b-4c3d-9e4f-5a6b7c8d9e01","name":"Investments"},""" +
                """"total":{"amount":"320.50","currency":"CAD"},"entryCount":2,"color":"#1d4ed8"}]}"""
        const val ACCOUNT_EXAMPLE =
            """{"from":"2026-10-01","to":"2026-10-31","total":{"amount":"5320.50","currency":"CAD"},""" +
                """"entryCount":3,"items":[""" +
                """{"account":{"id":"3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11","name":"Debit card"},""" +
                """"total":{"amount":"5000.00","currency":"CAD"},"entryCount":1,"color":"#e11d48"},""" +
                """{"account":{"id":"8b3e0f52-6d4c-4a9b-c2f7-3e5d9a1b0c44","name":"Brokerage"},""" +
                """"total":{"amount":"320.50","currency":"CAD"},"entryCount":2,"color":"#0891b2"}]}"""
    }
}
