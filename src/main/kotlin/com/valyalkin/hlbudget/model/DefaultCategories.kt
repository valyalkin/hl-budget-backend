package com.valyalkin.hlbudget.model

/** One of the categories every user starts with, and its fixed colour (feature 007, FR-001). */
data class DefaultCategory(
    val name: String,
    val color: String,
)

/** The categories every user starts with (FR-010). Part of the product; users cannot change them. */
object DefaultCategories {
    val NAMES: List<String> =
        listOf(
            "Rent",
            "Groceries",
            "Public Transport",
            "Car",
            "Household",
            "Health",
            "Beauty",
            "Clothes",
            "Education",
            "Travel",
            "Bills",
            "Subscriptions",
            "Other",
            "Sport",
            "Entertainment",
            "Pet",
            "Taxi",
        )

    /** Each default with its colour: the n-th default gets the n-th palette colour. */
    val ALL: List<DefaultCategory> = NAMES.zip(CategoryPalette.DEFAULTS) { name, color -> DefaultCategory(name, color) }
}
