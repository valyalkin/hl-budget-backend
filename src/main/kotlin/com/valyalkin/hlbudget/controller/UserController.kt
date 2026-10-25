package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.UserProfileResponse
import com.valyalkin.hlbudget.dto.toResponse
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * The caller's own record. No body, no path or query identifier: `me` is the token's subject.
 *
 * Returns ResponseEntity because the 201 owes a `Location` header — the one sanctioned case.
 */
@RestController
@RequestMapping(UserController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_USERS)
class UserController(
    private val users: UserService,
) {
    @PutMapping("/me")
    @Operation(
        operationId = "ensureMyProfile",
        summary = "Create or refresh the caller's user record and return it",
        description = DESCRIPTION,
    )
    @ApiResponse(
        responseCode = "200",
        description = "Existing record refreshed",
        headers = [Header(name = "X-Request-Id", ref = OpenApiConfig.REQUEST_ID_HEADER_REF)],
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = UserProfileResponse::class),
                examples = [ExampleObject(name = "returning", ref = "#/components/examples/ProfileReturning")],
            ),
        ],
    )
    @ApiResponse(
        responseCode = "201",
        description = "Record created on first call",
        headers = [
            Header(
                name = "Location",
                description = "Address of the created resource",
                schema = Schema(type = "string", example = "/api/v1/users/me"),
            ),
            Header(name = "X-Request-Id", ref = OpenApiConfig.REQUEST_ID_HEADER_REF),
        ],
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = UserProfileResponse::class),
                examples = [ExampleObject(name = "firstVisit", ref = "#/components/examples/ProfileFirstVisit")],
            ),
        ],
    )
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    fun ensureMyProfile(
        @Parameter(hidden = true) identity: VerifiedIdentity,
    ): ResponseEntity<UserProfileResponse> {
        val result = users.ensureProfile(identity)
        val body = result.user.toResponse()
        return if (result.created) {
            ResponseEntity.created(URI.create(ME_PATH)).body(body)
        } else {
            ResponseEntity.ok(body)
        }
    }

    companion object {
        const val BASE_PATH: String = "/api/v1/users"
        const val ME_PATH: String = "$BASE_PATH/me"

        private const val DESCRIPTION =
            "Identifies the caller only from the validated access token (`sub`). On the first call for a " +
                "`sub`, creates the user record and returns 201 with a `Location` header. On later calls, sets " +
                "`lastSeenAt` to now, refreshes `displayName`, `email` and `avatarUrl` from the token's claims, " +
                "and returns 200. Idempotent: concurrent first calls yield exactly one record. Takes no request " +
                "body and no user identifier."
    }
}
