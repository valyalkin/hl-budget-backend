package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.ImportRowCommand
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import org.springframework.stereotype.Component
import java.util.Locale
import java.util.UUID

/**
 * Turns a spending's category and account, each given by id or by name, into the caller's own
 * items (feature 003 research R-004, changed by feature 007 research R-008).
 *
 * - An id must name one of the caller's items; anything else is `NotFound`.
 * - A name is trimmed, must not be blank and has at most 50 characters. It must match one of the
 *   caller's items ignoring case; anything else is `NotFound`. Categories and accounts are created
 *   only in Settings, never here (feature 007, FR-020).
 *
 * Every problem is reported at once with the [earlier] ones (FR-005).
 */
@Component
class NamedItemResolver(
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {
    fun resolve(
        userId: UUID,
        command: SpendingCommand,
        earlier: List<FieldViolation>,
    ): Pair<Ref, Ref> {
        val violations = earlier.toMutableList()
        val category = choose(CATEGORY, command.categoryId, command.categoryName, violations)
        val account = choose(ACCOUNT, command.accountId, command.accountName, violations)
        val categoryRef = find(CATEGORY, category, userId, violations)
        val accountRef = find(ACCOUNT, account, userId, violations)
        if (violations.isNotEmpty() || categoryRef == null || accountRef == null) {
            throw ValidationException(violations)
        }
        return Pair(categoryRef, accountRef)
    }

    /** All of the caller's categories and accounts, keyed by [key], read in one query each. */
    fun names(userId: UUID): NameMaps =
        NameMaps(
            categories = categories.findAllByUserId(userId).associate { key(it.name) to Ref(it.id, it.name) },
            accounts = accounts.findAllByUserId(userId).associate { key(it.name) to Ref(it.id, it.name) },
        )

    /** The caller's items by name, looked up with [NamedItemResolver.key]. */
    data class NameMaps(
        val categories: Map<String, Ref>,
        val accounts: Map<String, Ref>,
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
        ): FieldViolation? =
            if (key(name) in items) {
                null
            } else {
                FieldViolation(
                    "#/spendings/$index/${kind}Name",
                    "spendings[$index].${kind}Name",
                    NOT_FOUND,
                    notYours(kind),
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
        violations: MutableList<FieldViolation>,
    ): Ref? {
        val (field, found) =
            when (choice) {
                is Choice.ById -> "${kind}Id" to byId(kind, choice.id, userId)
                is Choice.ByName -> "${kind}Name" to byName(kind, choice.name, userId)
                Choice.Invalid -> return null
            }
        if (found == null) violations += FieldViolation("#/$field", field, NOT_FOUND, notYours(kind))
        return found
    }

    private fun byId(
        kind: String,
        id: UUID,
        userId: UUID,
    ): Ref? =
        if (kind == CATEGORY) {
            categories.findByIdAndUserId(id, userId)?.let { Ref(it.id, it.name) }
        } else {
            accounts.findByIdAndUserId(id, userId)?.let { Ref(it.id, it.name) }
        }

    private fun byName(
        kind: String,
        name: String,
        userId: UUID,
    ): Ref? =
        if (kind == CATEGORY) {
            categories.findByUserIdAndNameIgnoreCase(userId, name)?.let { Ref(it.id, it.name) }
        } else {
            accounts.findByUserIdAndNameIgnoreCase(userId, name)?.let { Ref(it.id, it.name) }
        }

    companion object {
        private const val CATEGORY = "category"
        private const val ACCOUNT = "account"
        private const val MAX_NAME = 50
        private const val NOT_FOUND = "NotFound"

        private fun notYours(kind: String): String =
            if (kind == CATEGORY) "is not one of your categories" else "is not one of your accounts"

        /** How names are compared: ignoring case and surrounding spaces. */
        fun key(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}
