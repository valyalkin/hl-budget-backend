package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The homelab probe paths: UP without a token, and no components or details disclosed. */
class HealthIT : AbstractIntegrationTest() {
    @ParameterizedTest
    @ValueSource(strings = ["/healthz", "/readyz"])
    fun `the probe answers UP without a token and discloses nothing else`(path: String) {
        client
            .get()
            .uri(path)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .json("""{"status":"UP"}""", org.springframework.test.json.JsonCompareMode.STRICT)
    }
}
