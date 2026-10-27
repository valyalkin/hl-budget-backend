package com.valyalkin.hlbudget.model

/** Create or rename an account or category. [name] is already trimmed. */
data class NameCommand(
    val name: String,
)
