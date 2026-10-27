package com.valyalkin.hlbudget.dto.spending

import com.valyalkin.hlbudget.dto.toDto
import com.valyalkin.hlbudget.model.Spending

fun Spending.toResponse(): SpendingResponse =
    SpendingResponse(id, date, merchant, amount.toDto(), category.toDto(), account.toDto(), createdAt, updatedAt)
