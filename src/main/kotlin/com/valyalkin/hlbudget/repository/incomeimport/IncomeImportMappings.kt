package com.valyalkin.hlbudget.repository.incomeimport

import com.valyalkin.hlbudget.model.IncomeImport
import com.valyalkin.hlbudget.model.Money
import java.util.Currency

fun IncomeImportEntity.toModel(currency: Currency): IncomeImport =
    IncomeImport(
        id = id,
        incomeCount = incomeCount,
        firstDate = firstDate,
        lastDate = lastDate,
        total = Money(total, currency),
        createdAt = createdAt,
    )
