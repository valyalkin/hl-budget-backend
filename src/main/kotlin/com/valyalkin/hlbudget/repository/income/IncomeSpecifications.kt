package com.valyalkin.hlbudget.repository.income

import com.valyalkin.hlbudget.model.SpendingFilter
import com.valyalkin.hlbudget.model.SpendingSort
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.time.LocalDate
import java.util.UUID

/** The income list query, always scoped to one user; the twin of SpendingSpecifications. */
object IncomeSpecifications {
    fun matching(
        userId: UUID,
        filter: SpendingFilter,
    ): Specification<IncomeEntity> =
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
     * repeats an entry.
     */
    fun sortOf(sort: SpendingSort): Sort {
        val direction = if (sort.ascending) Sort.Direction.ASC else Sort.Direction.DESC
        val primary = if (sort.byAmount) "amount" else "date"
        return Sort.by(direction, primary, "createdAt", "id")
    }

    private fun forUser(userId: UUID) =
        Specification<IncomeEntity> { root, _, cb -> cb.equal(root.get<UUID>("userId"), userId) }

    private fun dateFrom(from: LocalDate) =
        Specification<IncomeEntity> { root, _, cb -> cb.greaterThanOrEqualTo(root.get("date"), from) }

    private fun dateTo(to: LocalDate) =
        Specification<IncomeEntity> { root, _, cb -> cb.lessThanOrEqualTo(root.get("date"), to) }

    private fun idIn(
        attribute: String,
        ids: Set<UUID>,
    ) = Specification<IncomeEntity> { root, _, _ -> root.get<UUID>(attribute).`in`(ids) }
}
