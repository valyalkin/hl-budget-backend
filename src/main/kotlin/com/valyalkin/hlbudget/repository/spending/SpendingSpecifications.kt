package com.valyalkin.hlbudget.repository.spending

import com.valyalkin.hlbudget.model.SpendingFilter
import com.valyalkin.hlbudget.model.SpendingSort
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.util.UUID

/** The spending list query, always scoped to one user (research R-010). */
object SpendingSpecifications {
    fun matching(
        userId: UUID,
        filter: SpendingFilter,
    ): Specification<SpendingEntity> =
        Specification.allOf(
            listOfNotNull(
                forUser(userId),
                filter.from?.let(::dateFrom),
                filter.to?.let(::dateTo),
                filter.categoryIds.takeIf { it.isNotEmpty() }?.let { ids -> idIn("categoryId", ids) },
                filter.accountIds.takeIf { it.isNotEmpty() }?.let { ids -> idIn("accountId", ids) },
            ),
        )

    /**
     * Ties are broken by creation time in the same direction, then by id, so paging never skips or
     * repeats a spending (FR-039).
     */
    fun sortOf(sort: SpendingSort): Sort {
        val direction = if (sort.ascending) Sort.Direction.ASC else Sort.Direction.DESC
        val primary = if (sort.byAmount) "amount" else "date"
        return Sort.by(direction, primary, "createdAt", "id")
    }

    private fun forUser(userId: UUID) =
        Specification<SpendingEntity> { root, _, cb -> cb.equal(root.get<UUID>("userId"), userId) }

    private fun dateFrom(from: LocalDate) =
        Specification<SpendingEntity> { root, _, cb -> cb.greaterThanOrEqualTo(root.get("date"), from) }

    private fun dateTo(to: LocalDate) =
        Specification<SpendingEntity> { root, _, cb -> cb.lessThanOrEqualTo(root.get("date"), to) }

    private fun idIn(
        attribute: String,
        ids: Set<UUID>,
    ) = Specification<SpendingEntity> { root, _, _ -> root.get<UUID>(attribute).`in`(ids) }
}
