package com.valyalkin.hlbudget.dto

import com.valyalkin.hlbudget.model.Ref
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(name = "Ref", description = "Another resource's id and current name")
data class RefDto(
    val id: UUID,
    @field:Schema(example = "Groceries")
    val name: String,
)

fun Ref.toDto(): RefDto = RefDto(id, name)
