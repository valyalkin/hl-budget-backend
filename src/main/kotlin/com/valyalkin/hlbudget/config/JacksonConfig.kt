package com.valyalkin.hlbudget.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature

/**
 * JSON rules that the error contract depends on.
 *
 * **Unknown properties are ignored**, which is Jackson 3's default and kept on purpose: a request
 * carrying a field this version does not know is accepted. That tolerance is what lets a client
 * and this service be deployed in either order, and what stops a newer caller from getting a 400
 * during a rolling release. The cost is real - a misspelled optional field is silently dropped -
 * so the defence against that belongs in the client's contract tests, not in a 400 from here.
 *
 * `ViolationExtractor` still knows how to name an unknown property, so a service that genuinely
 * owns both ends can switch `FAIL_ON_UNKNOWN_PROPERTIES` on and get a field-level error for free.
 *
 * Instants serialise as RFC 3339 strings rather than epoch numbers, so every timestamp this
 * service emits - including the one on a problem response - is readable and unambiguous.
 */
@Configuration
class JacksonConfig {
    @Bean
    fun jsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
}
