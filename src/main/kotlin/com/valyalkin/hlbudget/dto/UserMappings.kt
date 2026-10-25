package com.valyalkin.hlbudget.dto

import com.valyalkin.hlbudget.model.User

/** `subject` is deliberately not mapped: the Auth0 subject never leaves the backend. */
fun User.toResponse(): UserProfileResponse =
    UserProfileResponse(id, displayName, email, avatarUrl, firstSeenAt, lastSeenAt)
