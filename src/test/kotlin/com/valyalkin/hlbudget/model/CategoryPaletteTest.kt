package com.valyalkin.hlbudget.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.pow

/** Feature 007, research R-003 and R-004: the palette and how a new category gets its colour. */
class CategoryPaletteTest {
    private val spare = listOf("#e11d48", "#0891b2", "#a855f7", "#365314", "#1e3a8a", "#701a75", "#ea580c")

    @Test
    fun `the palette has 24 distinct lower-case hex colours in the agreed order`() {
        assertThat(CategoryPalette.COLORS).hasSize(24).doesNotHaveDuplicates()
        assertThat(CategoryPalette.COLORS).allMatch { it.matches(Regex("^#[0-9a-f]{6}$")) }
        assertThat(CategoryPalette.COLORS.first()).isEqualTo("#1e5b45")
        assertThat(CategoryPalette.SPARE).containsExactlyElementsOf(spare)
    }

    @Test
    fun `every colour has at least 3 to 1 contrast against the white card`() {
        assertThat(CategoryPalette.COLORS).allSatisfy { color ->
            assertThat(contrastAgainstWhite(color)).describedAs(color).isGreaterThanOrEqualTo(3.0)
        }
    }

    @Test
    fun `the 17 defaults get the first 17 colours, all different`() {
        assertThat(DefaultCategories.ALL).hasSize(17)
        assertThat(DefaultCategories.ALL.map { it.color }).containsExactlyElementsOf(CategoryPalette.COLORS.take(17))
        assertThat(DefaultCategories.ALL.map { it.color }).doesNotHaveDuplicates()
        assertThat(DefaultCategories.ALL.first()).isEqualTo(DefaultCategory("Rent", "#1e5b45"))
        assertThat(DefaultCategories.ALL.last()).isEqualTo(DefaultCategory("Taxi", "#8a6d00"))
        assertThat(DefaultCategories.NAMES).containsExactlyElementsOf(DefaultCategories.ALL.map { it.name })
    }

    @Test
    fun `new categories take the spare colours first, then cycle by least use`() {
        val counts = DefaultCategories.ALL.associate { it.color to 1 }.toMutableMap()
        val given = (1..9).map { CategoryPalette.next(counts).also { counts.merge(it, 1, Int::plus) } }

        assertThat(given.take(7)).containsExactlyElementsOf(spare)
        // All 24 are now used once; ties go to the spare colours first, in order.
        assertThat(given.drop(7)).containsExactly("#e11d48", "#0891b2")
    }

    @Test
    fun `after the spare colours are used twice, the default colours come next`() {
        val counts = DefaultCategories.ALL.associate { it.color to 1 }.toMutableMap()
        spare.forEach { counts[it] = 2 }

        assertThat(CategoryPalette.next(counts)).isEqualTo("#1e5b45")
    }

    @Test
    fun `a freed colour is chosen first`() {
        val counts = CategoryPalette.COLORS.associateWith { 1 }.toMutableMap()
        counts["#a855f7"] = 0

        assertThat(CategoryPalette.next(counts)).isEqualTo("#a855f7")
    }

    @Test
    fun `colours outside the palette do not count`() {
        val counts = DefaultCategories.ALL.associate { it.color to 1 } + ("#8a908b" to 5)

        assertThat(CategoryPalette.next(counts)).isEqualTo("#e11d48")
    }

    private fun contrastAgainstWhite(hex: String): Double {
        fun channel(index: Int): Double {
            val value = hex.substring(index, index + 2).toInt(16) / 255.0
            return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5)
        return 1.05 / (luminance + 0.05)
    }
}
