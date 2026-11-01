package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.NameRequest
import com.valyalkin.hlbudget.dto.incomecategory.IncomeCategoryListResponse
import com.valyalkin.hlbudget.dto.incomecategory.IncomeCategoryResponse
import com.valyalkin.hlbudget.dto.incomecategory.toListResponse
import com.valyalkin.hlbudget.dto.incomecategory.toResponse
import com.valyalkin.hlbudget.dto.toCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.IncomeCategoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/** The caller's income categories: Salary and Investments plus their own (feature 009, US4). */
@RestController
@RequestMapping(IncomeCategoryController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_INCOME_CATEGORIES)
class IncomeCategoryController(
    private val categories: IncomeCategoryService,
) {
    @GetMapping
    @Operation(
        operationId = "listIncomeCategories",
        summary = "List all of the caller's income categories, sorted by name, with income counts",
        description =
            "On the caller's first budgeting call Salary and Investments are created for them, exactly once, " +
                "so this never returns an empty list. Spending categories are never listed here. Not paged.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "All income categories",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = IncomeCategoryListResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun listIncomeCategories(
        @Parameter(hidden = true) identity: VerifiedIdentity,
    ): IncomeCategoryListResponse = categories.list(identity).toListResponse()

    @GetMapping("/{incomeCategoryId}")
    @Operation(operationId = "getIncomeCategory", summary = "Fetch one of the caller's income categories")
    @ApiResponse(
        responseCode = "200",
        description = "The income category",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = IncomeCategoryResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun getIncomeCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable incomeCategoryId: UUID,
    ): IncomeCategoryResponse = categories.get(identity, incomeCategoryId).toResponse()

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "createIncomeCategory", summary = "Create a custom income category")
    @ApiResponse(
        responseCode = "201",
        description = "Created, with a colour unused among the caller's income categories while any remain",
        headers = [Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF)],
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = IncomeCategoryResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.DUPLICATE_NAME)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun createIncomeCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Valid @RequestBody request: NameRequest,
    ): ResponseEntity<IncomeCategoryResponse> {
        val category = categories.create(identity, request.toCommand())
        return ResponseEntity.created(URI.create("$BASE_PATH/${category.id}")).body(category.toResponse())
    }

    @PutMapping("/{incomeCategoryId}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "renameIncomeCategory",
        summary = "Rename a custom income category; its colour is kept",
        description = "Salary and Investments cannot be renamed: 422 with `details.reason = default-category`.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Renamed",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = IncomeCategoryResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.DUPLICATE_NAME_OR_CONCURRENT)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_OR_DEFAULT_CATEGORY)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun renameIncomeCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable incomeCategoryId: UUID,
        @Valid @RequestBody request: NameRequest,
    ): IncomeCategoryResponse = categories.rename(identity, incomeCategoryId, request.toCommand()).toResponse()

    @DeleteMapping("/{incomeCategoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteIncomeCategory",
        summary = "Delete a custom income category that no income entry uses",
        description = "Salary and Investments cannot be deleted: 422 with `details.reason = default-category`.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.INCOME_CATEGORY_IN_USE)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.DEFAULT_CATEGORY)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun deleteIncomeCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable incomeCategoryId: UUID,
    ) = categories.delete(identity, incomeCategoryId)

    companion object {
        const val BASE_PATH: String = "/api/v1/income-categories"
    }
}
