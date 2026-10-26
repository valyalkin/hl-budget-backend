package com.valyalkin.hlbudget.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

/** US6-AS3: a missing or blank auth setting fails startup and names the property. */
class HlBudgetPropertiesTest {
    @Configuration
    @EnableConfigurationProperties(HlBudgetProperties::class)
    class Props

    private val complete =
        mapOf(
            "issuer" to "https://t.example/",
            "audience" to "https://api.example",
            "jwk-set-uri" to "https://t.example/.well-known/jwks.json",
            "claims-namespace" to "https://hl-budget/",
        )

    private fun runner(values: Map<String, String>) =
        ApplicationContextRunner()
            .withUserConfiguration(Props::class.java)
            .withPropertyValues(*values.map { (key, value) -> "hlbudget.auth.$key=$value" }.toTypedArray())

    private fun failureText(values: Map<String, String>): String {
        var text = ""
        runner(values).run { context ->
            assertThat(context).hasFailed()
            text =
                generateSequence(context.startupFailure) { it.cause }
                    .joinToString("\n") { it.message.orEmpty() }
        }
        return text
    }

    @Test
    fun `a complete configuration binds`() {
        runner(complete).run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBean(HlBudgetProperties::class.java).auth.claimsNamespace)
                .isEqualTo("https://hl-budget/")
        }
    }

    private fun camel(key: String) =
        key
            .split('-')
            .mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercase) }
            .joinToString("")

    @ParameterizedTest
    @ValueSource(strings = ["issuer", "audience", "jwk-set-uri", "claims-namespace"])
    fun `a missing setting fails startup naming it`(key: String) {
        assertThat(failureText(complete - key)).contains("hlbudget.auth.${camel(key)}").contains("must not be blank")
    }

    @ParameterizedTest
    @ValueSource(strings = ["issuer", "audience", "jwk-set-uri", "claims-namespace"])
    fun `a blank setting fails startup naming it`(key: String) {
        assertThat(
            failureText(complete + (key to " ")),
        ).contains("hlbudget.auth.${camel(key)}").contains("must not be blank")
    }

    /**
     * The real application.yaml, fed the deployment's env var names. An unset variable must fail
     * startup: Boot leaves an unresolvable placeholder as literal text, which would otherwise pass
     * @NotBlank and make the service reject every token instead of refusing to start.
     */
    @ParameterizedTest
    @ValueSource(
        strings = [
            "HLBUDGET_AUTH_ISSUER",
            "HLBUDGET_AUTH_AUDIENCE",
            "HLBUDGET_AUTH_JWKSETURI",
            "HLBUDGET_AUTH_CLAIMSNAMESPACE",
        ],
    )
    fun `an unset environment variable fails startup through application yaml`(variable: String) {
        val env =
            mapOf(
                "HLBUDGET_AUTH_ISSUER" to "https://t.example/",
                "HLBUDGET_AUTH_AUDIENCE" to "https://api.example",
                "HLBUDGET_AUTH_JWKSETURI" to "https://t.example/.well-known/jwks.json",
                "HLBUDGET_AUTH_CLAIMSNAMESPACE" to "https://hl-budget/",
            ) - variable
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Props::class.java)
            .withPropertyValues(*env.map { (key, value) -> "$key=$value" }.toTypedArray())
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).rootCause().hasMessageContaining("must not be blank")
            }
    }
}
