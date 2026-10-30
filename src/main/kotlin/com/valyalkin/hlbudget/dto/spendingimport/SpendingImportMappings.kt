package com.valyalkin.hlbudget.dto.spendingimport

import com.valyalkin.hlbudget.dto.toTotalDto
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.SpendingImport
import java.math.BigDecimal

/** Only called after validation, so each amount is a well-formed decimal. */
fun SpendingImportRequest.toCommands(): List<ImportRowCommand> =
    spendings.map { row ->
        ImportRowCommand(
            date = row.date,
            merchant = row.merchant.trim(),
            amount = BigDecimal(row.amount.amount),
            currency = row.amount.currency,
            categoryName = row.categoryName.trim(),
            accountName = row.accountName.trim(),
        )
    }

fun SpendingImport.toResponse(): SpendingImportResponse =
    SpendingImportResponse(
        id = id,
        spendingCount = spendingCount,
        firstDate = firstDate,
        lastDate = lastDate,
        total = total.toTotalDto(),
        createdAt = createdAt,
    )
