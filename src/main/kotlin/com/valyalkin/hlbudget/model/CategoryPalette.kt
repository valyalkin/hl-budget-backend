package com.valyalkin.hlbudget.model

/**
 * The colours categories are drawn in (feature 007, research R-003). Positions 1-17 belong to the
 * default categories, in [DefaultCategories] order; 18-24 are spare colours for custom categories.
 * Each is at least 3:1 against the white card. Stored categories keep their hex value, so changing
 * this list never recolours an existing category.
 *
 * Accounts use the same palette and the same [next] rule, counted over the user's accounts only, so
 * account and category colours are assigned independently (feature 008, research R-002).
 */
object CategoryPalette {
    val COLORS: List<String> =
        listOf(
            "#1e5b45",
            "#c2410c",
            "#1d4ed8",
            "#a16207",
            "#7e22ce",
            "#0f766e",
            "#be185d",
            "#4d7c0f",
            "#b91c1c",
            "#0369a1",
            "#57534e",
            "#4338ca",
            "#64748b",
            "#16a34a",
            "#c026d3",
            "#7c2d12",
            "#8a6d00",
            "#e11d48",
            "#0891b2",
            "#a855f7",
            "#365314",
            "#1e3a8a",
            "#701a75",
            "#ea580c",
        )

    private const val DEFAULT_COUNT = 17

    val DEFAULTS: List<String> = COLORS.take(DEFAULT_COUNT)
    val SPARE: List<String> = COLORS.drop(DEFAULT_COUNT)

    /** The order ties are broken in: spare colours first, then the defaults' colours. */
    private val TIE_ORDER: List<String> = SPARE + DEFAULTS

    /**
     * The colour for a new custom category or account (research R-004): the palette colour used by
     * the fewest of the user's categories (or accounts), given [counts] per colour. Colours outside
     * the palette are ignored.
     */
    fun next(counts: Map<String, Int>): String = TIE_ORDER.minBy { counts[it] ?: 0 }
}
