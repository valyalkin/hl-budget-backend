package com.valyalkin.hlbudget.repository.incomecategory

import com.valyalkin.hlbudget.model.IncomeCategory

fun IncomeCategoryRow.toModel(): IncomeCategory = IncomeCategory(id, name, isDefault, incomeCount, color)
