package com.valyalkin.hlbudget.dto.incomecategory

import com.fasterxml.jackson.annotation.JsonProperty
import com.valyalkin.hlbudget.model.IncomeCategory
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(
    name = "IncomeCategory",
    description = "What kind of income it is; a separate list from spending categories",
)
data class IncomeCategoryResponse(
    @field:Schema(example = "6e7f8091-2b3c-4d4e-8f5a-6b7c8d9e0f12")
    val id: UUID,
    @field:Schema(example = "Salary", maxLength = 50)
    val name: String,
    @get:Schema(
        description =
            "True for Salary and Investments, which every user starts with; they cannot be renamed or deleted",
        accessMode = Schema.AccessMode.READ_ONLY,
    )
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
    @field:Schema(
        description = "How many of the caller's income entries use this category",
        accessMode = Schema.AccessMode.READ_ONLY,
        minimum = "0",
    )
    val incomeCount: Long,
    @field:Schema(
        description =
            "The category's colour in charts. Fixed for Salary and Investments; assigned once to a custom " +
                "income category when it is created and kept after renaming",
        accessMode = Schema.AccessMode.READ_ONLY,
        example = "#1e5b45",
        pattern = "^#[0-9a-f]{6}$",
    )
    val color: String,
)

@Schema(
    name = "IncomeCategoryList",
    description = "All of the caller's income categories, sorted by name (never paged)",
)
data class IncomeCategoryListResponse(
    val items: List<IncomeCategoryResponse>,
)

fun IncomeCategory.toResponse(): IncomeCategoryResponse =
    IncomeCategoryResponse(id, name, isDefault, incomeCount, color)

fun List<IncomeCategory>.toListResponse(): IncomeCategoryListResponse =
    IncomeCategoryListResponse(map { it.toResponse() })
