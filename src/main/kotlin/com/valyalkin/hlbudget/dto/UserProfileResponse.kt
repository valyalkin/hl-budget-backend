package com.valyalkin.hlbudget.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(name = "UserProfile", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class UserProfileResponse(
    @field:Schema(
        description = "Opaque HL Budget identifier for the user. Not the Auth0 subject",
        example = "0b9f6c1e-3c1a-4f2e-9a57-2f1d6c2b8e11",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val id: UUID,
    @field:Schema(
        description = "Name from the identity provider; null when it has none",
        example = "Alex Example",
        maxLength = 255,
        types = ["string", "null"],
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val displayName: String?,
    @field:Schema(
        description = "Email from the identity provider; null when it has none",
        example = "alex@example.com",
        format = "email",
        maxLength = 320,
        types = ["string", "null"],
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val email: String?,
    @field:Schema(
        description = "HTTPS address of the avatar image; null when there is none",
        example = "https://lh3.googleusercontent.com/a/example",
        format = "uri",
        maxLength = 2048,
        types = ["string", "null"],
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val avatarUrl: String?,
    @field:Schema(
        description = "When the record was created (UTC)",
        example = "2026-10-25T09:15:00Z",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val firstSeenAt: Instant,
    @field:Schema(
        description = "When the user last called this operation (UTC)",
        example = "2026-10-26T18:02:41Z",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val lastSeenAt: Instant,
)
