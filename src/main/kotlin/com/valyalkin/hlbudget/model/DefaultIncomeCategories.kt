package com.valyalkin.hlbudget.model

/**
 * The income categories every user starts with, and their fixed colours (feature 009, research
 * R-002): palette positions 1 and 3, the same for everyone. Part of the product; users cannot
 * rename or delete them.
 */
object DefaultIncomeCategories {
    val ALL: List<DefaultCategory> =
        listOf(
            DefaultCategory("Salary", "#1e5b45"),
            DefaultCategory("Investments", "#1d4ed8"),
        )
}
