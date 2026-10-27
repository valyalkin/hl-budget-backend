package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.category.CategoryListResponse
import com.valyalkin.hlbudget.dto.category.CategoryResponse
import com.valyalkin.hlbudget.dto.category.toListResponse
import com.valyalkin.hlbudget.dto.category.toResponse
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.CategoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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

    companion object {
        const val BASE_PATH: String = "/api/v1/categories"
    }
}
