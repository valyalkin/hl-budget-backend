package com.valyalkin.hlbudget.model

/** The orders the spending list supports, each with its API value (research R-010). */
enum class SpendingSort(
    val apiValue: String,
    val byAmount: Boolean,
    val ascending: Boolean,
) {
    DATE_DESC("date,desc", byAmount = false, ascending = false),
    DATE_ASC("date,asc", byAmount = false, ascending = true),
    AMOUNT_DESC("amount,desc", byAmount = true, ascending = false),
    AMOUNT_ASC("amount,asc", byAmount = true, ascending = true),
    ;

    companion object {
        val DEFAULT: SpendingSort = DATE_DESC
        val API_VALUES: List<String> = entries.map { it.apiValue }

        fun fromApiValue(value: String): SpendingSort? = entries.firstOrNull { it.apiValue == value }
    }
}
