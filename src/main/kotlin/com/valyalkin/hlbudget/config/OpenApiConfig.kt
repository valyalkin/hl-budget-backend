package com.valyalkin.hlbudget.config

import com.valyalkin.hlbudget.error.ProblemCodes
import com.valyalkin.hlbudget.error.ProblemDetailFactory
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The OpenAPI document at /api/v3/api-docs. OpenApiExportIT writes it to contracts/api.yaml,
 * which the frontend generates its types from; contracts/openapi.yaml in the spec is the target.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("HL Budget API")
                    .version("1.0.0")
                    .description(DESCRIPTION),
            )
            // Relative, so the exported contract does not change with the test's random port.
            .servers(listOf(Server().url("/").description("Same host as the web app; the backend serves /api")))
            .tags(
                listOf(
                    Tag().name(TAG_USERS).description("The signed-in user's own record"),
                    Tag().name(TAG_ACCOUNTS).description("Where the caller's money was paid from"),
                    Tag()
                        .name(TAG_CATEGORIES)
                        .description("What the caller's money was spent on, including the 17 defaults"),
                    Tag().name(TAG_SPENDINGS).description("The caller's spending transactions"),
                    Tag()
                        .name(TAG_INCOMES)
                        .description("The caller's income entries and their past sources (feature 009)"),
                    Tag()
                        .name(TAG_INCOME_CATEGORIES)
                        .description("What kind of income it is, including Salary and Investments"),
                    Tag()
                        .name(TAG_TOTALS)
                        .description("Sums of the caller's spendings and income by month, category and account"),
                    Tag()
                        .name(TAG_SPENDING_IMPORTS)
                        .description("Adding many spendings at once, all or nothing"),
                    Tag()
                        .name(TAG_INCOME_IMPORTS)
                        .description("Adding many income entries at once, all or nothing"),
                    Tag()
                        .name(TAG_MERCHANTS)
                        .description("Merchants derived from the caller's spendings, for autofill"),
                    Tag()
                        .name(TAG_OPERATIONS)
                        .description("Unversioned probes for the platform (not routed through the ingress)"),
                ),
            ).components(OpenApiBudgetComponents.register(OpenApiComponents.components()))
            .paths(OpenApiProbes.probePaths())
            .addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))

    /**
     * Every property of the feature 002 response schemas is non-null in Kotlin, so all of them are
     * required in the contract. Without this the frontend's generated types would make every
     * response field optional.
     */
    @Bean
    fun requiredResponseFields(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.components?.schemas.orEmpty().forEach { (name, schema) ->
                if (name in FULLY_REQUIRED_SCHEMAS) {
                    schema.required =
                        schema.properties
                            .orEmpty()
                            .keys
                            .toList()
                }
            }
        }

    companion object {
        private val FULLY_REQUIRED_SCHEMAS =
            setOf(
                "Account",
                "AccountList",
                "Category",
                "CategoryList",
                "Spending",
                "SpendingPage",
                "Money",
                "Ref",
                "MoneyTotal",
                "MonthTotal",
                "MonthlyTotals",
                "CategoryTotal",
                "CategoryTotals",
                "AccountTotal",
                "AccountTotals",
                "Merchant",
                "MerchantList",
                "Income",
                "IncomePage",
                "IncomeCategory",
                "IncomeCategoryList",
                "IncomeSource",
                "IncomeSourceList",
                "IncomeMonthTotal",
                "IncomeMonthlyTotals",
                "IncomeCategoryTotal",
                "IncomeCategoryTotals",
                "IncomeAccountTotal",
                "IncomeAccountTotals",
            )

        const val BEARER_SCHEME = "bearerAuth"
        const val TAG_USERS = "Users"
        const val TAG_OPERATIONS = "Operations"
        const val TAG_ACCOUNTS = "Accounts"
        const val TAG_CATEGORIES = "Categories"
        const val TAG_SPENDINGS = "Spendings"
        const val TAG_TOTALS = "Totals"
        const val TAG_MERCHANTS = "Merchants"
        const val TAG_SPENDING_IMPORTS = "Spending imports"
        const val TAG_INCOMES = "Incomes"
        const val TAG_INCOME_CATEGORIES = "Income categories"
        const val TAG_INCOME_IMPORTS = "Income imports"
        const val PROBLEM_SCHEMA = "Problem"
        const val FIELD_VIOLATION_SCHEMA = "FieldViolation"
        const val HEALTH_SCHEMA = "HealthStatus"
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_HEADER_REF = "#/components/headers/X-Request-Id"

        internal val PROBLEM_TYPES =
            listOf(
                ProblemCodes.UNAUTHENTICATED,
                ProblemCodes.ACCESS_DENIED,
                ProblemCodes.BAD_REQUEST,
                ProblemCodes.NOT_FOUND,
                ProblemCodes.CONFLICT,
                ProblemCodes.UNPROCESSABLE_ENTITY,
                ProblemCodes.INTERNAL_ERROR,
                ProblemCodes.SERVICE_UNAVAILABLE,
            ).map { ProblemDetailFactory.TYPE_PREFIX + it }

        private val DESCRIPTION =
            """
            Backend for HL Budget. Every business endpoint is versioned under `/api/v{N}` and requires an
            Auth0 access token for the HL Budget API audience. Every error is RFC 9457
            `application/problem+json` with a stable `type` URN and a `traceId`.
            """.trimIndent()
    }
}
