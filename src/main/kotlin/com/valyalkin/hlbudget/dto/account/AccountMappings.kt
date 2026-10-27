package com.valyalkin.hlbudget.dto.account

import com.valyalkin.hlbudget.model.Account

fun Account.toResponse(): AccountResponse = AccountResponse(id, name, spendingCount)

fun List<Account>.toListResponse(): AccountListResponse = AccountListResponse(map { it.toResponse() })
