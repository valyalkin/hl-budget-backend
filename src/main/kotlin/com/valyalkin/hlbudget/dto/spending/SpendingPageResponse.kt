package com.valyalkin.hlbudget.dto.spending

import com.valyalkin.hlbudget.model.SpendingPage
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "SpendingPage",
    description = "One page of the caller's spendings; `items` is empty when nothing matches",
)
data class SpendingPageResponse(
    val items: List<SpendingResponse>,
    @field:Schema(description = "0-based page number", minimum = "0")
    val page: Int,
    @field:Schema(minimum = "1", maximum = "100")
    val size: Int,
    @field:Schema(description = "Spendings matching the filters, across all pages")
    val totalItems: Long,
    val totalPages: Int,
)

fun SpendingPage.toResponse(): SpendingPageResponse =
    SpendingPageResponse(items.map { it.toResponse() }, page, size, totalItems, totalPages)
