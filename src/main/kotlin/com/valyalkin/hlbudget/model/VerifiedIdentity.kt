package com.valyalkin.hlbudget.model

/**
 * Who is calling, taken only from a validated access token (FR-015, FR-017). The service accepts
 * nothing else as the caller's identity.
 */
data class VerifiedIdentity(
    val subject: String,
    val displayName: String?,
    val email: String?,
    val avatarUrl: String?,
)
