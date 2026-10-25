package com.valyalkin.failures

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only endpoints that provoke failures no real request can. Lives outside the application's
 * scan root so the component scan does not register it twice; tests add it as a @Bean.
 */
@RestController
@RequestMapping("/api/v1/test-failures")
class FailureController {
    @GetMapping("/unexpected")
    fun unexpected(): Nothing = error("column secret_internal_name does not exist")
}
