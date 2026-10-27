package com.valyalkin.hlbudget.dto.category

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(name = "Category", description = "What the caller's money was spent on")
data class CategoryResponse(
    @field:Schema(example = "2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55")
    val id: UUID,
    @field:Schema(example = "Groceries", maxLength = 50)
    val name: String,
    @get:Schema(
        description = "True for the 17 categories every user starts with; they cannot be renamed or deleted",
        accessMode = Schema.AccessMode.READ_ONLY,
    )
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
    @field:Schema(
        description = "How many of the caller's spendings use this category",
        accessMode = Schema.AccessMode.READ_ONLY,
        minimum = "0",
    )
    val spendingCount: Long,
)

@Schema(name = "CategoryList", description = "All of the caller's categories, sorted by name (never paged)")
data class CategoryListResponse(
    val items: List<CategoryResponse>,
)
