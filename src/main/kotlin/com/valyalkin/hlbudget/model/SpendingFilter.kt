package com.valyalkin.hlbudget.model

import java.time.LocalDate
import java.util.UUID

/**
 * What the spending list should return. Each filter is optional; ids within one filter match any
 * of them, and the filters combine with AND (FR-038). [sort] is the raw API value, checked by the
 * service so an unknown one is reported like any other invalid field.
 */
data class SpendingFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val categoryIds: Set<UUID> = emptySet(),
    val accountIds: Set<UUID> = emptySet(),
    val sort: String? = null,
)

/** One page of the list, with totals across all pages. */
data class SpendingPage(
    val items: List<Spending>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
