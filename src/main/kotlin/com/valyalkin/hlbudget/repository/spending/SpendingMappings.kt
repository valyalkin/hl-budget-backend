package com.valyalkin.hlbudget.repository.spending

import com.valyalkin.hlbudget.model.Money
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.Spending
import java.util.Currency

fun SpendingEntity.toModel(
    category: Ref,
    account: Ref,
    currency: Currency,
): Spending = Spending(id, date, merchant, Money(amount, currency), category, account, createdAt, updatedAt)
