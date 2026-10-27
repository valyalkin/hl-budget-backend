package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.CurrentUserRepository
import com.valyalkin.hlbudget.repository.CurrentUserRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CurrentUserServiceTest {
    private val users = mockk<CurrentUserRepository>(relaxed = true)
    private val service = CurrentUserService(users)
    private val identity = VerifiedIdentity("auth0|alice", "Alice", "alice@example.com", null)
    private val id = UUID.randomUUID()

    @Test
    fun `an existing user is resolved with one lookup`() {
        every { users.findBySubject("auth0|alice") } returns CurrentUserRow(id, Instant.now())

        assertThat(service.resolve(identity)).isEqualTo(id)

        verify(exactly = 1) { users.findBySubject("auth0|alice") }
        verify(exactly = 0) { users.insertIfMissing(any()) }
    }

    @Test
    fun `a missing user is inserted, then looked up again`() {
        every { users.findBySubject("auth0|alice") } returnsMany listOf(null, CurrentUserRow(id, Instant.now()))

        assertThat(service.resolve(identity)).isEqualTo(id)

        verifyOrder {
            users.findBySubject("auth0|alice")
            users.insertIfMissing("auth0|alice")
            users.findBySubject("auth0|alice")
        }
    }
}
