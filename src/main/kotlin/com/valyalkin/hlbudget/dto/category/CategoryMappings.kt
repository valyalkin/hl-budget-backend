package com.valyalkin.hlbudget.dto.category

import com.valyalkin.hlbudget.model.Category

fun Category.toResponse(): CategoryResponse = CategoryResponse(id, name, isDefault, spendingCount)

fun List<Category>.toListResponse(): CategoryListResponse = CategoryListResponse(map { it.toResponse() })
