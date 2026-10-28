package com.valyalkin.hlbudget.service

import com.valyalkin.hlbudget.model.SpendingSort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SpendingSortTest {
    @ParameterizedTest
    @CsvSource(
        "'date,desc', DATE_DESC",
        "'date,asc', DATE_ASC",
        "'amount,desc', AMOUNT_DESC",
        "'amount,asc', AMOUNT_ASC",
    )
    fun `the four API values parse`(
        value: String,
        expected: SpendingSort,
    ) {
        assertThat(SpendingSort.fromApiValue(value)).isEqualTo(expected)
        assertThat(expected.apiValue).isEqualTo(value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["merchant,asc", "date", "date,up", "", "DATE,DESC"])
    fun `anything else is unknown`(value: String) {
        assertThat(SpendingSort.fromApiValue(value)).isNull()
    }

    @org.junit.jupiter.api.Test
    fun `the default is newest date first`() {
        assertThat(SpendingSort.DEFAULT).isEqualTo(SpendingSort.DATE_DESC)
        assertThat(SpendingSort.API_VALUES).containsExactly("date,desc", "date,asc", "amount,desc", "amount,asc")
    }
}
