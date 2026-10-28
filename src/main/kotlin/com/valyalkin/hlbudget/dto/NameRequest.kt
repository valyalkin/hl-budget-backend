package com.valyalkin.hlbudget.dto

import com.valyalkin.hlbudget.model.NameCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** The body for creating or renaming an account or category: the name is all there is. */
@Schema(name = "NameRequest")
data class NameRequest(
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description = "Trimmed before use; unique per user ignoring case",
        example = "Amex",
        minLength = 1,
        maxLength = MAX_NAME,
    )
    val name: String,
) {
    companion object {
        const val MAX_NAME = 50
    }
}

fun NameRequest.toCommand(): NameCommand = NameCommand(name.trim())
