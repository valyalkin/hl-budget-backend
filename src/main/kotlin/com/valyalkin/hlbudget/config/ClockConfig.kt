package com.valyalkin.hlbudget.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** The one source of "now". Tests replace it with a fixed clock (research R-006). */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
