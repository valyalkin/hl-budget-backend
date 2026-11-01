package com.valyalkin.hlbudget.model

/**
 * The two kinds of entry the app records (feature 009, research R-004): spendings and income. Each
 * holds the constant table and column names the shared SQL is built from, so totals and past-name
 * queries exist once. These identifiers come only from this enum, never from a request.
 */
enum class Ledger(
    val table: String,
    val dateColumn: String,
    val partyColumn: String,
    val categoryTable: String,
) {
    SPENDINGS("spendings", "spending_date", "merchant", "categories"),
    INCOMES("incomes", "income_date", "source", "income_categories"),
}
