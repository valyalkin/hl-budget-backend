package com.valyalkin.hlbudget.dto.spendingimport

import com.valyalkin.hlbudget.dto.MoneyDto
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

/** The rows of an import, in the order the user reviewed them (feature 004). */
@Schema(name = "SpendingImportRequest", description = "The rows to import, in the order the user reviewed them")
data class SpendingImportRequest(
    @field:NotNull(message = "is required")
    @field:Size(min = 1, max = MAX_ROWS, message = "must have 1 to 1000 rows")
    @field:Valid
    @field:ArraySchema(
        minItems = 1,
        maxItems = MAX_ROWS,
        schema = Schema(implementation = SpendingImportRowRequest::class),
    )
    val spendings: List<SpendingImportRowRequest>,
) {
    companion object {
        const val MAX_ROWS = 1000
    }
}

/** One row: the same rules as SpendingRequest, with the category and account by name. */
@Schema(
    name = "SpendingImportRow",
    description = "One spending. The same rules as SpendingRequest, with category and account by name",
)
data class SpendingImportRowRequest(
    @field:NotNull(message = "is required")
    @field:Schema(description = "Today or earlier, in the app's time zone", example = "2026-10-28")
    val date: LocalDate,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_MERCHANT, message = "must be at most 255 characters")
    @field:Schema(example = "Corner Bakery", minLength = 1, maxLength = MAX_MERCHANT)
    val merchant: String,
    @field:NotNull(message = "is required")
    @field:Valid
    val amount: MoneyDto,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description =
            "Matched against the caller's categories ignoring case and surrounding spaces; created when none matches",
        example = "Groceries",
        minLength = 1,
        maxLength = MAX_NAME,
    )
    val categoryName: String,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description =
            "Matched against the caller's accounts ignoring case and surrounding spaces; created when none matches",
        example = "Visa",
        minLength = 1,
        maxLength = MAX_NAME,
    )
    val accountName: String,
) {
    companion object {
        const val MAX_MERCHANT = 255
        const val MAX_NAME = 50
    }
}
