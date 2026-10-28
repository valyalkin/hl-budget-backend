package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.spending.MerchantQueries
import com.valyalkin.hlbudget.repository.spending.MerchantRow
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/** Feature 003, research R-005: merchants for autofill. */
class MerchantServiceTest {
    private val currentUser = mockk<CurrentUserService>()
    private val queries = mockk<MerchantQueries>()
    private val service = MerchantService(currentUser, queries)
    private val identity = VerifiedIdentity("auth0|alice", null, null, null)
    private val userId = UUID.randomUUID()

    private fun row(name: String) =
        MerchantRow(name, 2, LocalDate.parse("2026-10-20"), UUID.randomUUID(), "Groceries", UUID.randomUUID(), "Visa")

    @Test
    fun `one extra row tells that more merchants exist`() {
        every { currentUser.resolve(identity) } returns userId
        every { queries.mostUsed(userId, 3) } returns listOf(row("A"), row("B"), row("C"))

        val list = service.mostUsed(identity, 2)

        assertThat(list.items.map { it.name }).containsExactly("A", "B")
        assertThat(list.truncated).isTrue()
        assertThat(list.items[0].lastCategory.name).isEqualTo("Groceries")
        assertThat(list.items[0].lastAccount.name).isEqualTo("Visa")
    }

    @Test
    fun `fewer rows than the limit is not truncated`() {
        every { currentUser.resolve(identity) } returns userId
        every { queries.mostUsed(userId, 11) } returns listOf(row("A"))

        assertThat(service.mostUsed(identity, 10).truncated).isFalse()
    }
}
