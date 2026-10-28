package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.NameRequest
import com.valyalkin.hlbudget.dto.category.CategoryListResponse
import com.valyalkin.hlbudget.dto.category.CategoryResponse
import com.valyalkin.hlbudget.dto.category.toListResponse
import com.valyalkin.hlbudget.dto.category.toResponse
import com.valyalkin.hlbudget.dto.toCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.CategoryService
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

/** The caller's categories: the 17 defaults plus their own (US1, US5). */
@RestController
@RequestMapping(CategoryController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_CATEGORIES)
class CategoryController(
    private val categories: CategoryService,
) {
    @GetMapping
    @Operation(
        operationId = "listCategories",
        summary = "List all of the caller's categories, sorted by name, with spending counts",
        description =
            "On the caller's first budgeting call the 17 default categories are created for them, exactly " +
                "once, so this never returns an empty list. Not paged: a person has tens of categories.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "All categories",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = CategoryListResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun listCategories(
        @Parameter(hidden = true) identity: VerifiedIdentity,
    ): CategoryListResponse = categories.list(identity).toListResponse()

    @GetMapping("/{categoryId}")
    @Operation(operationId = "getCategory", summary = "Fetch one of the caller's categories")
    @ApiResponse(
        responseCode = "200",
        description = "The category",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = CategoryResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun getCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable categoryId: UUID,
    ): CategoryResponse = categories.get(identity, categoryId).toResponse()

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "createCategory", summary = "Create a custom category")
    @ApiResponse(
        responseCode = "201",
        description = "Created; `isDefault` is always false",
        headers = [Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF)],
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = CategoryResponse::class),
            ),
        ],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.DUPLICATE_NAME)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun createCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Valid @RequestBody request: NameRequest,
    ): ResponseEntity<CategoryResponse> {
        val category = categories.create(identity, request.toCommand())
        return ResponseEntity.created(URI.create("$BASE_PATH/${category.id}")).body(category.toResponse())
    }

    @PutMapping("/{categoryId}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "renameCategory",
        summary = "Rename a custom category",
        description = "Default categories cannot be renamed: 422 with `details.reason = default-category`.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Renamed",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = CategoryResponse::class),
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
    fun renameCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable categoryId: UUID,
        @Valid @RequestBody request: NameRequest,
    ): CategoryResponse = categories.rename(identity, categoryId, request.toCommand()).toResponse()

    @DeleteMapping("/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "deleteCategory",
        summary = "Delete a custom category that no spending uses",
        description = "Default categories cannot be deleted: 422 with `details.reason = default-category`.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.IN_USE)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.DEFAULT_CATEGORY)
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun deleteCategory(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable categoryId: UUID,
    ) = categories.delete(identity, categoryId)

    companion object {
        const val BASE_PATH: String = "/api/v1/categories"
    }
}
