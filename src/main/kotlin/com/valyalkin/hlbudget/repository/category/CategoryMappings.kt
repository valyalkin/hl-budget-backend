package com.valyalkin.hlbudget.repository.category

import com.valyalkin.hlbudget.model.Category

fun CategoryRow.toModel(): Category = Category(id, name, isDefault, spendingCount)
