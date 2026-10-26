package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.UpsertResult
import com.valyalkin.hlbudget.model.User
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class UserServiceTest {
    private val repository = mockk<UserRepository>()
    private val service = UserService(repository)

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `ensureProfile passes the identity to the upsert and returns its result unchanged`(created: Boolean) {
        val identity = VerifiedIdentity("auth0|abc", "Alex", "alex@example.com", null)
        val now = Instant.parse("2026-10-25T09:15:00Z")
        val result =
            UpsertResult(User(UUID.randomUUID(), "auth0|abc", "Alex", "alex@example.com", null, now, now), created)
        every { repository.upsert(identity) } returns result

        assertThat(service.ensureProfile(identity)).isSameAs(result)
        verify(exactly = 1) { repository.upsert(identity) }
    }
}
