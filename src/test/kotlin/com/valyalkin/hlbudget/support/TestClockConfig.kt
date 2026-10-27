package com.valyalkin.hlbudget.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * A clock that keeps ticking but starts at [NOW], so "today" in America/Toronto is always
 * [TODAY] in integration tests while created and updated times still move forward.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfig {
    @Bean
    @Primary
    fun testClock(): Clock = Clock.offset(Clock.system(ZoneOffset.UTC), Duration.between(Instant.now(), NOW))

    companion object {
        val NOW: Instant = Instant.parse("2026-10-27T15:00:00Z")
        const val TODAY: String = "2026-10-27"
        const val TOMORROW: String = "2026-10-28"
    }
}
