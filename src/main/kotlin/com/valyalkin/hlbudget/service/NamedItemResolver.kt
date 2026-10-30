package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.FieldViolation
import com.valyalkin.hlbudget.error.ValidationException
import com.valyalkin.hlbudget.model.Ref
import com.valyalkin.hlbudget.model.SpendingCommand
import com.valyalkin.hlbudget.repository.NamedItemInserts
import com.valyalkin.hlbudget.repository.NamedItemRow
import com.valyalkin.hlbudget.repository.account.AccountRepository
import com.valyalkin.hlbudget.repository.category.CategoryRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Turns a spending's category and account, each given by id or by name, into the caller's own
 * items (research R-004).
 *
 * - An id must name one of the caller's items; anything else is `NotFound`, as in feature 002.
 * - A name is trimmed, must not be blank and has at most 50 characters. It reuses the caller's
 *   item with the same name ignoring case, or creates one.
 *
 * Every problem is reported at once with the [earlier] ones (FR-005). Nothing is created unless
 * the whole request is valid, so a refused spending never leaves a new item behind.
 */
@Component
class NamedItemResolver(
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val inserts: NamedItemInserts,
    private val clock: Clock,
) {
    fun resolve(
        userId: UUID,
        command: SpendingCommand,
        earlier: List<FieldViolation>,
    ): Pair<Ref, Ref> {
        val violations = earlier.toMutableList()
        val category = choose(CATEGORY, command.categoryId, command.categoryName, violations)
        val account = choose(ACCOUNT, command.accountId, command.accountName, violations)
        val categoryById = (category as? Choice.ById)?.let { owned(CATEGORY, it.id, userId, violations) }
        val accountById = (account as? Choice.ById)?.let { owned(ACCOUNT, it.id, userId, violations) }
        if (violations.isNotEmpty()) throw ValidationException(violations)

        val now = Instant.now(clock)
        return Pair(
            categoryById ?: byName(CATEGORY, category, userId, now),
            accountById ?: byName(ACCOUNT, account, userId, now),
        )
    }

    /**
     * Finds or creates each distinct name once (feature 004, FR-032): `Visa`, `visa ` and `VISA`
     * are one account. Names are trimmed; the maps are keyed by [key]. Names are handled in sorted
     * order so two imports creating the same names never wait on each other in opposite orders.
     */
    fun resolveNames(
        userId: UUID,
        categoryNames: Collection<String>,
        accountNames: Collection<String>,
        now: Instant,
    ): NameMaps =
        NameMaps(
            categories =
                distinct(categoryNames).associate {
                    key(it) to
                        inserts.findOrCreateCategory(userId, it, now).toRef()
                },
            accounts =
                distinct(accountNames).associate {
                    key(it) to
                        inserts.findOrCreateAccount(userId, it, now).toRef()
                },
        )

    /** Resolved names, looked up with [NamedItemResolver.key]. */
    data class NameMaps(
        val categories: Map<String, Ref>,
        val accounts: Map<String, Ref>,
    ) {
        fun category(name: String): Ref = categories.getValue(key(name))

        fun account(name: String): Ref = accounts.getValue(key(name))
    }

    private fun distinct(names: Collection<String>): List<String> =
        names.map { it.trim() }.distinctBy { key(it) }.sortedBy { key(it) }

    private fun NamedItemRow.toRef() = Ref(id, name)

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

    private fun owned(
        kind: String,
        id: UUID,
        userId: UUID,
        violations: MutableList<FieldViolation>,
    ): Ref? {
        val found =
            if (kind == CATEGORY) {
                categories.findByIdAndUserId(id, userId)?.let { Ref(it.id, it.name) }
            } else {
                accounts.findByIdAndUserId(id, userId)?.let { Ref(it.id, it.name) }
            }
        if (found == null) {
            val plural = if (kind == CATEGORY) "categories" else "accounts"
            violations += FieldViolation("#/${kind}Id", "${kind}Id", "NotFound", "is not one of your $plural")
        }
        return found
    }

    private fun byName(
        kind: String,
        choice: Choice,
        userId: UUID,
        now: Instant,
    ): Ref {
        check(choice is Choice.ByName) { "Only reached when the $kind was given by a valid name" }
        val row: NamedItemRow =
            if (kind == CATEGORY) {
                inserts.findOrCreateCategory(userId, choice.name, now)
            } else {
                inserts.findOrCreateAccount(userId, choice.name, now)
            }
        return Ref(row.id, row.name)
    }

    companion object {
        private const val CATEGORY = "category"
        private const val ACCOUNT = "account"
        private const val MAX_NAME = 50

        /** How names are compared: ignoring case and surrounding spaces. */
        fun key(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}
