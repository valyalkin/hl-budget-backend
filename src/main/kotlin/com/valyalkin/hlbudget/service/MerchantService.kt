package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.Merchant
import com.valyalkin.hlbudget.model.MerchantList
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.spending.MerchantQueries
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** The caller's past merchants for autofill (feature 003, FR-057, FR-058, research R-005). */
@Service
@Transactional
class MerchantService(
    private val currentUser: CurrentUserService,
    private val queries: MerchantQueries,
) {
    /** Up to [limit] merchants, most used first; one extra row is read to tell whether more exist. */
    fun mostUsed(
        identity: VerifiedIdentity,
        limit: Int,
    ): MerchantList {
        val rows = queries.mostUsed(currentUser.resolve(identity), limit + 1)
        val items =
            rows.take(limit).map {
                Merchant(
                    name = it.name,
                    spendingCount = it.spendingCount,
                    lastUsedOn = it.lastUsedOn,
                    lastCategory = Ref(it.categoryId, it.categoryName),
                    lastAccount = Ref(it.accountId, it.accountName),
                )
            }
        return MerchantList(items, truncated = rows.size > limit)
    }
}
