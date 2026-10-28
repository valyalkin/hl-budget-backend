package com.valyalkin.hlbudget.repository.account

import com.valyalkin.hlbudget.model.Account

fun AccountRow.toModel(): Account = Account(id, name, spendingCount)
