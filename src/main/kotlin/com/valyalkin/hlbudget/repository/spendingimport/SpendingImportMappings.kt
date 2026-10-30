package com.valyalkin.hlbudget.repository.spendingimport

import com.valyalkin.hlbudget.model.Money
import com.valyalkin.hlbudget.model.SpendingImport
import java.util.Currency

fun SpendingImportEntity.toModel(currency: Currency): SpendingImport =
    SpendingImport(
        id = id,
        spendingCount = spendingCount,
        firstDate = firstDate,
        lastDate = lastDate,
        total = Money(total, currency),
        createdAt = createdAt,
    )
