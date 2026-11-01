package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.PROBLEM_SCHEMA
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.REQUEST_ID_HEADER
import com.valyalkin.hlbudget.config.OpenApiConfig.Companion.REQUEST_ID_HEADER_REF
import com.valyalkin.hlbudget.error.ProblemCodes
import com.valyalkin.hlbudget.error.ProblemDetailFactory
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import org.springframework.http.HttpStatus

/**
 * The error responses shared by the accounts, categories and spendings endpoints (feature 002),
 * each with the example a client will actually see. Controllers reference them by name, e.g.
 * `@ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)`.
 */
internal object OpenApiBudgetComponents {
    const val BAD_REQUEST = "#/components/responses/BadRequest"
    const val NOT_FOUND = "#/components/responses/NotFound"
    const val DUPLICATE_NAME = "#/components/responses/DuplicateName"
    const val DUPLICATE_NAME_OR_CONCURRENT = "#/components/responses/DuplicateNameOrConcurrent"
    const val CONCURRENT_MODIFICATION = "#/components/responses/ConcurrentModification"
    const val IN_USE = "#/components/responses/InUse"
    const val DEFAULT_CATEGORY = "#/components/responses/DefaultCategory"
    const val VALIDATION_FAILED = "#/components/responses/ValidationFailed"
    const val VALIDATION_OR_DEFAULT_CATEGORY = "#/components/responses/ValidationOrDefaultCategory"
    const val SPENDING_VALIDATION_FAILED = "#/components/responses/SpendingValidationFailed"
    const val LAST_ACCOUNT = "#/components/responses/LastAccount"
    const val SPENDING_IMPORT_VALIDATION_FAILED = "#/components/responses/SpendingImportValidationFailed"
    const val LOCATION_HEADER_REF = "#/components/headers/Location"
    const val INCOME_VALIDATION_FAILED = "#/components/responses/IncomeValidationFailed"
    const val INCOME_IMPORT_VALIDATION_FAILED = "#/components/responses/IncomeImportValidationFailed"
    const val INCOME_CATEGORY_IN_USE = "#/components/responses/IncomeCategoryInUse"

    private const val BAD = 400
    private const val NOT_FOUND_STATUS = 404
    private const val CONFLICT = 409
    private const val UNPROCESSABLE = 422
    private const val SPENDING_ID = "8d3a6b1f-4c2e-4f7a-9b0d-1e2c3a4b5c66"
    private const val ACCOUNT_ID = "3f1c5a8e-2b7d-4e0a-9c61-0d2f4b8a7e11"
    private const val IMPORTS_PATH = "/api/v1/spending-imports"
    private const val INCOMES_PATH = "/api/v1/incomes"

    private val nameInvalid =
        problem(
            UNPROCESSABLE,
            "1 field is invalid: name",
            "/api/v1/accounts",
            errors = listOf(violation("#/name", "name", "NotBlank", "must not be blank")),
        )
    private val duplicateName =
        problem(
            CONFLICT,
            "An account named \"amex\" already exists",
            "/api/v1/accounts",
            details = mapOf("reason" to "duplicate-name", "name" to "amex"),
        )
    private val concurrent =
        problem(
            CONFLICT,
            "The resource was modified concurrently. Re-read it and retry.",
            "/api/v1/accounts/$ACCOUNT_ID",
            details = mapOf("reason" to "concurrent-modification"),
        )
    private val defaultCategory =
        problem(
            UNPROCESSABLE,
            "Groceries is a default category and cannot be changed",
            "/api/v1/categories/2c8e4f6a-1b3d-4e5f-9a7b-8c0d1e2f3a55",
            details = mapOf("reason" to "default-category"),
        )

    fun register(components: Components): Components {
        components.addHeaders(
            "Location",
            Header()
                .description("Address of the resource")
                .schema(StringSchema().example("/api/v1/spendings/8d3a6b1f-4c2e-4f7a-9b0d-1e2c3a4b5c66")),
        )
        (protocolResponses() + conflictResponses() + validationResponses() + importResponses() + incomeResponses())
            .forEach { (name, response) -> components.addResponses(name, response) }
        return components
    }

    private fun protocolResponses(): Map<String, ApiResponse> =
        mapOf(
            "BadRequest" to
                response(
                    "The request cannot be read: the body is not JSON, or a path id is not a UUID",
                    "malformed" to
                        problem(BAD, "The path segment spendingId must be a valid UUID.", "/api/v1/spendings/abc"),
                ),
            "NotFound" to
                response(
                    "No such resource for the caller. Also returned for another user's resource, so existence " +
                        "is never revealed",
                    "notFound" to
                        problem(
                            NOT_FOUND_STATUS,
                            "Spending $SPENDING_ID was not found",
                            "/api/v1/spendings/$SPENDING_ID",
                            details = mapOf("resource" to "spending", "spendingId" to SPENDING_ID),
                        ),
                ),
        )

    private fun conflictResponses(): Map<String, ApiResponse> =
        mapOf(
            "DuplicateName" to
                response(
                    "Another of the caller's accounts or categories already has this name, ignoring case " +
                        "(`details.reason = duplicate-name`)",
                    "duplicateName" to duplicateName,
                ),
            "DuplicateNameOrConcurrent" to
                response(
                    "`details.reason` is `duplicate-name` or `concurrent-modification` (re-read and retry)",
                    "duplicateName" to duplicateName,
                    "concurrent" to concurrent,
                ),
            "ConcurrentModification" to
                response("Lost a concurrent write; re-read and retry", "concurrent" to concurrent),
            "InUse" to
                response(
                    "Entries still use this account or category; move or delete them first " +
                        "(`details.reason = in-use`, `details.spendingCount`; for an account also " +
                        "`details.incomeCount`, the income entries that use it)",
                    "inUseByIncome" to
                        problem(
                            CONFLICT,
                            "Account $ACCOUNT_ID is used by 2 income entries",
                            "/api/v1/accounts/$ACCOUNT_ID",
                            details = mapOf("reason" to "in-use", "spendingCount" to 0, "incomeCount" to 2),
                        ),
                    "inUse" to
                        problem(
                            CONFLICT,
                            "Account $ACCOUNT_ID is used by 2 spendings",
                            "/api/v1/accounts/$ACCOUNT_ID",
                            details = mapOf("reason" to "in-use", "spendingCount" to 2),
                        ),
                ),
        )

    private fun validationResponses(): Map<String, ApiResponse> =
        mapOf(
            "LastAccount" to
                response(
                    "The caller's only account cannot be deleted; add another account first " +
                        "(`details.reason = last-account`)",
                    "lastAccount" to
                        problem(
                            UNPROCESSABLE,
                            "Account $ACCOUNT_ID is the only account",
                            "/api/v1/accounts/$ACCOUNT_ID",
                            details = mapOf("reason" to "last-account"),
                        ),
                ),
            "DefaultCategory" to
                response(
                    "Default categories cannot be renamed or deleted (`details.reason = default-category`)",
                    "defaultCategory" to defaultCategory,
                ),
            "ValidationFailed" to
                response(
                    "One or more fields or parameters are invalid; each one is listed in `errors` with a JSON " +
                        "Pointer and a stable `code`",
                    "invalid" to nameInvalid,
                ),
            "ValidationOrDefaultCategory" to
                response(
                    "An invalid field (`errors`), or the category is a default (`details.reason = default-category`)",
                    "invalid" to nameInvalid,
                    "defaultCategory" to defaultCategory,
                ),
            "SpendingValidationFailed" to
                response(
                    "Invalid fields, each listed in `errors`. Codes include Required, TypeMismatch, NotBlank, Size, " +
                        "Pattern, Positive, UnsupportedCurrency, NotFuture, NotFound (an unknown or another " +
                        "user's category or account, by id or by name; a name never creates one) and " +
                        "ExactlyOne (give exactly one of categoryId and " +
                        "categoryName, and of accountId and accountName). On create only, " +
                        "`details.reason = idempotency-key-reused` when the Idempotency-Key was already used " +
                        "for a different spending",
                    "invalid" to spendingInvalid,
                    "unknownCategoryName" to unknownCategoryName,
                    "keyReused" to
                        problem(
                            UNPROCESSABLE,
                            "The Idempotency-Key was already used for a different spending",
                            "/api/v1/spendings",
                            details = mapOf("reason" to "idempotency-key-reused"),
                        ),
                ),
        )

    private fun importResponses(): Map<String, ApiResponse> =
        mapOf(
            "SpendingImportValidationFailed" to
                response(
                    "Nothing was created. Every invalid field of every row is listed in `errors`, its pointer " +
                        "naming the row by its 0-based position (`#/spendings/3/amount/amount`). Codes are those of " +
                        "SpendingRequest (Required, TypeMismatch, NotBlank, Size, Pattern, Positive, " +
                        "UnsupportedCurrency, NotFuture), NotFound on a category or account name that " +
                        "matches none of the caller's items (an import never creates one), plus Size on " +
                        "`#/spendings` for 0 or more " +
                        "than 1000 rows. " +
                        "`details.reason = idempotency-key-reused` when the Idempotency-Key was already used for " +
                        "different rows",
                    "invalidRows" to
                        problem(
                            UNPROCESSABLE,
                            "2 fields are invalid: spendings[1].date, spendings[4].amount.amount",
                            IMPORTS_PATH,
                            errors =
                                listOf(
                                    violation(
                                        "#/spendings/1/date",
                                        "spendings[1].date",
                                        "NotFuture",
                                        "must be today or earlier",
                                    ),
                                    violation(
                                        "#/spendings/4/amount/amount",
                                        "spendings[4].amount.amount",
                                        "Positive",
                                        "must be greater than 0",
                                    ),
                                ),
                        ),
                    "unknownNames" to unknownImportNames,
                    "tooMany" to
                        problem(
                            UNPROCESSABLE,
                            "1 field is invalid: spendings",
                            IMPORTS_PATH,
                            errors = listOf(violation("#/spendings", "spendings", "Size", "must have 1 to 1000 rows")),
                        ),
                    "keyReused" to
                        problem(
                            UNPROCESSABLE,
                            "The Idempotency-Key was already used for a different import",
                            IMPORTS_PATH,
                            details = mapOf("reason" to "idempotency-key-reused"),
                        ),
                ),
        )

    /** Feature 009: the income twins of the spending validation and in-use responses. */
    private fun incomeResponses(): Map<String, ApiResponse> =
        mapOf(
            "IncomeValidationFailed" to
                response(
                    "Invalid fields, each listed in `errors`, with the same codes as SpendingValidationFailed. " +
                        "NotFound marks an unknown or another user's income category or account, by id or by " +
                        "name; a spending category's name is not an income category, and a name never creates " +
                        "one. On create only, `details.reason = idempotency-key-reused` when the " +
                        "Idempotency-Key was already used for a different income",
                    "unknownCategoryName" to
                        problem(
                            UNPROCESSABLE,
                            "1 field is invalid: categoryName",
                            INCOMES_PATH,
                            errors =
                                listOf(
                                    violation(
                                        "#/categoryName",
                                        "categoryName",
                                        "NotFound",
                                        "is not one of your income categories",
                                    ),
                                ),
                        ),
                ),
            "IncomeImportValidationFailed" to
                response(
                    "Nothing was created. Every invalid field of every row is listed in `errors`, its pointer " +
                        "naming the row by its 0-based position (`#/incomes/3/amount/amount`), with the codes " +
                        "of SpendingImportValidationFailed; NotFound on a name that matches none of the " +
                        "caller's income categories or accounts",
                    "unknownCategory" to
                        problem(
                            UNPROCESSABLE,
                            "1 field is invalid: incomes[1].categoryName",
                            "/api/v1/income-imports",
                            errors =
                                listOf(
                                    violation(
                                        "#/incomes/1/categoryName",
                                        "incomes[1].categoryName",
                                        "NotFound",
                                        "is not one of your income categories",
                                    ),
                                ),
                        ),
                ),
            "IncomeCategoryInUse" to
                response(
                    "Income entries still use this income category; move or delete them first " +
                        "(`details.reason = in-use`, `details.incomeCount`)",
                    "inUse" to
                        problem(
                            CONFLICT,
                            "Income category 7f8091a2-3c4d-4e5f-9a6b-7c8d9e0f1a23 is used by 1 income entries",
                            "/api/v1/income-categories/7f8091a2-3c4d-4e5f-9a6b-7c8d9e0f1a23",
                            details = mapOf("reason" to "in-use", "incomeCount" to 1),
                        ),
                ),
        )

    /** Feature 007: a name that matches none of the caller's items is refused, never created. */
    private val unknownCategoryName
        get() =
            problem(
                UNPROCESSABLE,
                "1 field is invalid: categoryName",
                "/api/v1/spendings",
                errors =
                    listOf(
                        violation("#/categoryName", "categoryName", "NotFound", "is not one of your categories"),
                    ),
            )

    private val unknownImportNames
        get() =
            problem(
                UNPROCESSABLE,
                "2 fields are invalid: spendings[2].categoryName, spendings[5].accountName",
                IMPORTS_PATH,
                errors =
                    listOf(
                        violation(
                            "#/spendings/2/categoryName",
                            "spendings[2].categoryName",
                            "NotFound",
                            "is not one of your categories",
                        ),
                        violation(
                            "#/spendings/5/accountName",
                            "spendings[5].accountName",
                            "NotFound",
                            "is not one of your accounts",
                        ),
                    ),
            )

    private val spendingInvalid
        get() =
            problem(
                UNPROCESSABLE,
                "3 fields are invalid: date, amount.amount, categoryId",
                "/api/v1/spendings",
                errors =
                    listOf(
                        violation("#/date", "date", "NotFuture", "must be today or earlier"),
                        violation("#/amount/amount", "amount.amount", "Positive", "must be greater than 0"),
                        violation("#/categoryId", "categoryId", "NotFound", "is not one of your categories"),
                    ),
            )

    private fun response(
        description: String,
        vararg examples: Pair<String, Map<String, Any>>,
    ): ApiResponse =
        ApiResponse()
            .description(description)
            .addHeaderObject(REQUEST_ID_HEADER, Header().`$ref`(REQUEST_ID_HEADER_REF))
            .content(
                Content().addMediaType(
                    "application/problem+json",
                    MediaType()
                        .schema(Schema<Any>().`$ref`("#/components/schemas/$PROBLEM_SCHEMA"))
                        .apply { examples.forEach { (name, value) -> addExamples(name, Example().value(value)) } },
                ),
            )

    private fun problem(
        status: Int,
        detail: String,
        instance: String,
        details: Map<String, Any> = emptyMap(),
        errors: List<Map<String, String>> = emptyList(),
    ): Map<String, Any> =
        linkedMapOf<String, Any>(
            "type" to ProblemDetailFactory.TYPE_PREFIX + ProblemCodes.forStatus(status),
            "title" to (HttpStatus.resolve(status)?.reasonPhrase ?: "Error"),
            "status" to status,
            "detail" to detail,
            "instance" to instance,
            "timestamp" to "2026-10-27T14:03:11Z",
            "traceId" to "4be0baf6ee174255",
        ).apply {
            if (details.isNotEmpty()) put("details", details)
            if (errors.isNotEmpty()) put("errors", errors)
        }

    private fun violation(
        pointer: String,
        field: String,
        code: String,
        message: String,
    ): Map<String, String> = linkedMapOf("pointer" to pointer, "field" to field, "code" to code, "message" to message)
}
