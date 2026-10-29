package com.valyalkin.hlbudget.dto.merchant

import com.valyalkin.hlbudget.dto.RefDto
import com.valyalkin.hlbudget.dto.toDto
import com.valyalkin.hlbudget.model.MerchantList
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(name = "Merchant", description = "A merchant derived from the caller's spendings")
data class MerchantResponse(
    @field:Schema(description = "Spelling on the most recent spending", example = "Neighbourhood Market")
    val name: String,
    @field:Schema(example = "12", minimum = "1")
    val spendingCount: Long,
    @field:Schema(example = "2026-10-27")
    val lastUsedOn: LocalDate,
    @field:Schema(description = "Category of the most recent spending")
    val lastCategory: RefDto,
    @field:Schema(description = "Account of the most recent spending")
    val lastAccount: RefDto,
)

@Schema(name = "MerchantList", description = "The caller's merchants, most used first")
data class MerchantListResponse(
    val items: List<MerchantResponse>,
    @field:Schema(description = "True when the caller has more merchants than `limit`")
    val truncated: Boolean,
)

fun MerchantList.toResponse(): MerchantListResponse =
    MerchantListResponse(
        items =
            items.map {
                MerchantResponse(
                    it.name,
                    it.spendingCount,
                    it.lastUsedOn,
                    it.lastCategory.toDto(),
                    it.lastAccount.toDto(),
                )
            },
        truncated = truncated,
    )
