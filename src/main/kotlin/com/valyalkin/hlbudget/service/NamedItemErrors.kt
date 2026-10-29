package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.error.ConflictException
import com.valyalkin.hlbudget.error.DuplicateException
import com.valyalkin.hlbudget.error.NotFoundException
import com.valyalkin.hlbudget.error.UnprocessableEntityException
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

/**
 * The refusals accounts and categories share (research R-004, R-005). [resource] is the lower-case
 * name used in messages and `details` (`account`, `category`).
 */
internal fun notFound(
    resource: String,
    id: UUID,
) = NotFoundException(
    "${resource.replaceFirstChar(Char::uppercase)} $id was not found",
    mapOf("resource" to resource, "${resource}Id" to id),
)

internal fun duplicateName(
    resource: String,
    name: String,
    cause: Throwable? = null,
) = DuplicateException(
    "${if (resource.first() in "aeiou") "An" else "A"} $resource named \"$name\" already exists",
    mapOf("reason" to "duplicate-name", "name" to name),
    cause,
)

internal fun inUse(
    resource: String,
    id: UUID,
    count: Long,
    cause: Throwable? = null,
) = ConflictException(
    "${resource.replaceFirstChar(Char::uppercase)} $id is used by $count spendings",
    mapOf("reason" to "in-use", "spendingCount" to count),
    cause,
)

/** The caller's only account cannot be deleted (feature 003, FR-086, research R-006). */
internal fun lastAccount(id: UUID) =
    UnprocessableEntityException(
        "Account $id is the only account",
        mapOf("reason" to "last-account"),
    )

/** A lost race on the case-insensitive unique index gets the same answer as the pre-check. */
internal fun <T> saveOrDuplicate(
    resource: String,
    name: String,
    save: () -> T,
): T =
    try {
        save()
    } catch (exception: DataIntegrityViolationException) {
        throw duplicateName(resource, name, exception)
    }
