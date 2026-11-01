package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.Ledger
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import com.valyalkin.hlbudget.repository.incomecategory.IncomeCategoryRepository
import org.springframework.stereotype.Component
import java.util.Locale
import java.util.UUID

/**
 * Turns an entry's category and account, each given by id or by name, into the caller's own
 * items (feature 003 research R-004, changed by feature 007 research R-008).
 *
 * - An id must name one of the caller's items; anything else is `NotFound`.
 * - A name is trimmed, must not be blank and has at most 50 characters. It must match one of the
 *   caller's items ignoring case; anything else is `NotFound`. Categories and accounts are created
 *   only in Settings, never here (feature 007, FR-020).
 * - The [Ledger] decides which categories count: a spending's are the spending categories, an
 *   income's the income categories (feature 009, FR-013). Accounts are shared.
 *
 * Every problem is reported at once with the [earlier] ones (FR-005).
 */
@Component
class NamedItemResolver(
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val incomeCategories: IncomeCategoryRepository,
) {
    fun resolve(
        userId: UUID,
        command: SpendingCommand,
        earlier: List<FieldViolation>,
        ledger: Ledger = Ledger.SPENDINGS,
    ): Pair<Ref, Ref> {
        val violations = earlier.toMutableList()
        val category = choose(CATEGORY, command.categoryId, command.categoryName, violations)
        val account = choose(ACCOUNT, command.accountId, command.accountName, violations)
        val categoryRef = find(CATEGORY, category, userId, ledger, violations)
        val accountRef = find(ACCOUNT, account, userId, ledger, violations)
        if (violations.isNotEmpty() || categoryRef == null || accountRef == null) {
            throw ValidationException(violations)
        }
        return Pair(categoryRef, accountRef)
    }

    /** All of the caller's categories (of [ledger]) and accounts, keyed by [key], read in one query each. */
    fun names(
        userId: UUID,
        ledger: Ledger = Ledger.SPENDINGS,
    ): NameMaps {
        val categoryRefs =
            if (ledger == Ledger.INCOMES) {
                incomeCategories.findAllByUserId(userId).associate { key(it.name) to Ref(it.id, it.name) }
            } else {
                categories.findAllByUserId(userId).associate { key(it.name) to Ref(it.id, it.name) }
            }
        return NameMaps(
            categories = categoryRefs,
            accounts = accounts.findAllByUserId(userId).associate { key(it.name) to Ref(it.id, it.name) },
            ledger = ledger,
        )
    }

    /** The caller's items by name, looked up with [NamedItemResolver.key]. */
    data class NameMaps(
        val categories: Map<String, Ref>,
        val accounts: Map<String, Ref>,
        val ledger: Ledger = Ledger.SPENDINGS,
    ) {
        fun category(name: String): Ref = categories.getValue(key(name))

        fun account(name: String): Ref = accounts.getValue(key(name))

        /** An import row's names that match nothing (feature 007, FR-025), at the row's pointers. */
        fun violations(
            row: ImportRowCommand,
            index: Int,
        ): List<FieldViolation> =
            listOfNotNull(
                missing(categories, row.categoryName, index, CATEGORY),
                missing(accounts, row.accountName, index, ACCOUNT),
            )

        private fun missing(
            items: Map<String, Ref>,
            name: String,
            index: Int,
            kind: String,
        ): FieldViolation? {
            if (key(name) in items) return null
            val collection = ledger.table
            return FieldViolation(
                "#/$collection/$index/${kind}Name",
                "$collection[$index].${kind}Name",
                NOT_FOUND,
                notYours(kind, ledger),
            )
        }
    }

    private sealed interface Choice {
        data class ById(
            val id: UUID,
        ) : Choice

        data class ByName(
            val name: String,
        ) : Choice

        data object Invalid : Choice
    }

    private fun choose(
        kind: String,
        id: UUID?,
        name: String?,
        violations: MutableList<FieldViolation>,
    ): Choice {
        val idField = "${kind}Id"
        val nameField = "${kind}Name"
        return when {
            id != null && name == null -> {
                Choice.ById(id)
            }

            id == null && name != null -> {
                nameChoice(nameField, name.trim(), violations)
            }

            else -> {
                violations +=
                    FieldViolation("#/$idField", idField, "ExactlyOne", "give exactly one of $idField and $nameField")
                Choice.Invalid
            }
        }
    }

    private fun nameChoice(
        field: String,
        name: String,
        violations: MutableList<FieldViolation>,
    ): Choice {
        val violation =
            when {
                name.isEmpty() -> FieldViolation("#/$field", field, "NotBlank", "must not be blank")
                name.length > MAX_NAME -> FieldViolation("#/$field", field, "Size", "must be at most 50 characters")
                else -> null
            }
        if (violation == null) return Choice.ByName(name)
        violations += violation
        return Choice.Invalid
    }

    /** The caller's item for a valid [choice], or `null` with a `NotFound` violation at its field. */
    private fun find(
        kind: String,
        choice: Choice,
        userId: UUID,
        ledger: Ledger,
        violations: MutableList<FieldViolation>,
    ): Ref? {
        val (field, found) =
            when (choice) {
                is Choice.ById -> "${kind}Id" to lookup(kind, ledger, userId, id = choice.id)
                is Choice.ByName -> "${kind}Name" to lookup(kind, ledger, userId, name = choice.name)
                Choice.Invalid -> return null
            }
        if (found == null) violations += FieldViolation("#/$field", field, NOT_FOUND, notYours(kind, ledger))
        return found
    }

    /** By [id] when given, otherwise by [name], among the items of [kind] that [ledger] uses. */
    private fun lookup(
        kind: String,
        ledger: Ledger,
        userId: UUID,
        id: UUID? = null,
        name: String = "",
    ): Ref? =
        when {
            kind == ACCOUNT -> account(userId, id, name)
            ledger == Ledger.INCOMES -> incomeCategory(userId, id, name)
            else -> category(userId, id, name)
        }

    private fun account(
        userId: UUID,
        id: UUID?,
        name: String,
    ): Ref? {
        val found =
            if (id != null) {
                accounts.findByIdAndUserId(id, userId)
            } else {
                accounts.findByUserIdAndNameIgnoreCase(userId, name)
            }
        return found?.let { Ref(it.id, it.name) }
    }

    private fun category(
        userId: UUID,
        id: UUID?,
        name: String,
    ): Ref? {
        val found =
            if (id != null) {
                categories.findByIdAndUserId(id, userId)
            } else {
                categories.findByUserIdAndNameIgnoreCase(userId, name)
            }
        return found?.let { Ref(it.id, it.name) }
    }

    private fun incomeCategory(
        userId: UUID,
        id: UUID?,
        name: String,
    ): Ref? {
        val found =
            if (id != null) {
                incomeCategories.findByIdAndUserId(id, userId)
            } else {
                incomeCategories.findByUserIdAndNameIgnoreCase(userId, name)
            }
        return found?.let { Ref(it.id, it.name) }
    }

    companion object {
        private const val CATEGORY = "category"
        private const val ACCOUNT = "account"
        private const val MAX_NAME = 50
        private const val NOT_FOUND = "NotFound"

        private fun notYours(
            kind: String,
            ledger: Ledger,
        ): String =
            when {
                kind == ACCOUNT -> "is not one of your accounts"
                ledger == Ledger.INCOMES -> "is not one of your income categories"
                else -> "is not one of your categories"
            }

        /** How names are compared: ignoring case and surrounding spaces. */
        fun key(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}
