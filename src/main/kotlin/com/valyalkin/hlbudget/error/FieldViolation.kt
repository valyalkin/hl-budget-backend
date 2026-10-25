package com.valyalkin.hlbudget.error

/**
 * One thing that was wrong with the request, in the `errors` member of the problem response.
 *
 * [pointer] is an RFC 6901 JSON Pointer into the request body (`#/lines/2/quantity`), which stays
 * unambiguous for nested objects and array elements where a dotted name does not. [field] is the
 * same location in dotted form, because it is what a human reads first.
 *
 * [code] is stable and machine-readable - the Bean Validation constraint name (`NotBlank`,
 * `Min`), or `Required`, `TypeMismatch`, `Unknown`, `Malformed`. Clients branch on [code];
 * [message] is for people and may be reworded at any time.
 *
 * There is deliberately no `rejectedValue`: request bodies carry personal data and problem
 * responses get logged downstream, so echoing the input back is a leak waiting to happen. The
 * expected type goes in [message] instead.
 */
data class FieldViolation(
    val pointer: String,
    val field: String,
    val code: String,
    val message: String,
)
