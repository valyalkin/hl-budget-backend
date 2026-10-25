package com.valyalkin.hlbudget.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Registers the token-backed [VerifiedIdentityResolver] for controller parameters. */
@Configuration
class WebConfig(
    private val identityResolver: VerifiedIdentityResolver,
) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(identityResolver)
    }
}
