package com.valyalkin.failures

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Test-only endpoints that provoke failures no real request can, plus one of each validation
 * shape. Lives outside the application's scan root so the component scan does not register it
 * twice; tests add it as a @Bean.
 */
@RestController
@Validated
@RequestMapping("/api/v1/test-failures")
class FailureController {
    data class Payload(
        @field:NotBlank(message = "must not be blank") val name: String,
        val count: Int,
    )

    @GetMapping("/unexpected")
    fun unexpected(): Nothing = error("column secret_internal_name does not exist")

    @PostMapping("/body")
    fun body(
        @Valid @RequestBody payload: Payload,
    ): Payload = payload

    @GetMapping("/query")
    fun query(
        @RequestParam @Max(10) size: Int,
    ): Int = size

    @GetMapping("/header")
    fun header(
        @RequestHeader("X-Thing") @Size(max = 3) thing: String,
    ): String = thing

    @GetMapping("/things/{id}")
    fun path(
        @PathVariable id: UUID,
    ): UUID = id
}

/** No @Validated: Spring's built-in method validation handles the constraints instead. */
@RestController
@RequestMapping("/api/v1/test-method-validation")
class MethodValidationController {
    @PostMapping("/body")
    @Suppress("UnusedParameter")
    fun body(
        @RequestHeader("X-Thing", required = false) @Size(max = 3) thing: String?,
        @Valid @RequestBody payload: FailureController.Payload,
    ): FailureController.Payload = payload
}
