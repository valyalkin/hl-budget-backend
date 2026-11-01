package com.valyalkin.hlbudget.repository.income

import com.valyalkin.hlbudget.model.Income
import com.valyalkin.hlbudget.model.Money
import com.valyalkin.hlbudget.model.Ref
import java.util.Currency

fun IncomeEntity.toModel(
    category: Ref,
    account: Ref,
    currency: Currency,
): Income = Income(id, date, source, Money(amount, currency), category, account, createdAt, updatedAt)
