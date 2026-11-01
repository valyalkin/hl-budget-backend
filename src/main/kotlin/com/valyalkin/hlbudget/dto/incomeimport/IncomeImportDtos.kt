package com.valyalkin.hlbudget.dto.incomeimport

import com.valyalkin.hlbudget.dto.MoneyDto
import com.valyalkin.hlbudget.dto.MoneyTotalDto
import com.valyalkin.hlbudget.dto.toTotalDto
import com.valyalkin.hlbudget.model.IncomeImport
import com.valyalkin.hlbudget.model.IncomeImportRowCommand
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The rows of an income import, in the order the user reviewed them (feature 009, US5). */
@Schema(name = "IncomeImportRequest", description = "The income rows to import, in the order the user reviewed them")
data class IncomeImportRequest(
    @field:NotNull(message = "is required")
    @field:Size(min = 1, max = MAX_ROWS, message = "must have 1 to 1000 rows")
    @field:Valid
    @field:ArraySchema(
        minItems = 1,
        maxItems = MAX_ROWS,
        schema = Schema(implementation = IncomeImportRowRequest::class),
    )
    val incomes: List<IncomeImportRowRequest>,
) {
    companion object {
        const val MAX_ROWS = 1000
    }
}

/** One row: the same rules as IncomeRequest, with the category and account by name. */
@Schema(
    name = "IncomeImportRow",
    description = "One income entry. The same rules as IncomeRequest, with category and account by name",
)
data class IncomeImportRowRequest(
    @field:NotNull(message = "is required")
    @field:Schema(description = "Today or earlier, in the app's time zone", example = "2026-10-15")
    val date: LocalDate,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_SOURCE, message = "must be at most 255 characters")
    @field:Schema(example = "Acme Corp", minLength = 1, maxLength = MAX_SOURCE)
    val source: String,
    @field:NotNull(message = "is required")
    @field:Valid
    val amount: MoneyDto,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description =
            "Matched against the caller's income categories ignoring case and surrounding spaces. Refused " +
                "(422, NotFound) when none matches; never creates a category",
        example = "Salary",
        minLength = 1,
        maxLength = MAX_NAME,
    )
    val categoryName: String,
    @field:NotNull(message = "is required")
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = MAX_NAME, message = "must be at most 50 characters")
    @field:Schema(
        description =
            "Matched against the caller's accounts ignoring case and surrounding spaces. Refused (422, " +
                "NotFound) when none matches; never creates an account",
        example = "Debit card",
        minLength = 1,
        maxLength = MAX_NAME,
    )
    val accountName: String,
) {
    companion object {
        const val MAX_SOURCE = 255
        const val MAX_NAME = 50
    }
}

/** The receipt of a completed income import. Every field is always present. */
@Schema(name = "IncomeImport", description = "The receipt of a completed income import. Holds nothing from the file")
data class IncomeImportResponse(
    @field:Schema(requiredMode = REQUIRED, example = "4c2d1e0f-9a8b-4c7d-8e6f-5a4b3c2d1e0f")
    val id: UUID,
    @field:Schema(
        requiredMode = REQUIRED,
        description = "How many income entries were created",
        minimum = "1",
        maximum = "1000",
        example = "2",
    )
    val incomeCount: Int,
    @field:Schema(requiredMode = REQUIRED, description = "The earliest date in the import", example = "2026-10-15")
    val firstDate: LocalDate,
    @field:Schema(
        requiredMode = REQUIRED,
        description = "The latest date in the import; clients show this month afterwards",
        example = "2026-10-20",
    )
    val lastDate: LocalDate,
    @field:Schema(requiredMode = REQUIRED)
    val total: MoneyTotalDto,
    @field:Schema(requiredMode = REQUIRED, example = "2026-10-29T14:03:11Z")
    val createdAt: Instant,
)

/** Only called after validation, so each amount is a well-formed decimal. `merchant` carries the source. */
fun IncomeImportRequest.toCommands(): List<IncomeImportRowCommand> =
    incomes.map { row ->
        IncomeImportRowCommand(
            date = row.date,
            merchant = row.source.trim(),
            amount = BigDecimal(row.amount.amount),
            currency = row.amount.currency,
            categoryName = row.categoryName.trim(),
            accountName = row.accountName.trim(),
        )
    }

fun IncomeImport.toResponse(): IncomeImportResponse =
    IncomeImportResponse(
        id = id,
        incomeCount = incomeCount,
        firstDate = firstDate,
        lastDate = lastDate,
        total = total.toTotalDto(),
        createdAt = createdAt,
    )
