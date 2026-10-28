package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDate

/** US4, FR-037 to FR-039: paged, filtered, sorted, stable. */
class SpendingListIT : AbstractIntegrationTest() {
    private val base = "/api/v1/spendings"
    private lateinit var groceries: String
    private lateinit var taxi: String
    private lateinit var rent: String
    private lateinit var amex: String
    private lateinit var cash: String

    /** Every id alice created, with its date, amount, category and account. */
    private data class Row(
        val id: String,
        val date: LocalDate,
        val amount: BigDecimal,
        val category: String,
        val account: String,
    )

    private val rows = mutableListOf<Row>()

    @BeforeEach
    fun seed() {
        val ids = categoryIds(alice)
        groceries = ids.getValue("Groceries")
        taxi = ids.getValue("Taxi")
        rent = ids.getValue("Rent")
        amex = createAccount(alice, "Amex")
        cash = createAccount(alice, "Cash")
        val categories = listOf(groceries, taxi, rent)
        val accounts = listOf(amex, cash)
        // 30 spendings from 15 September to 27 October ("today"), two per date, amounts repeating.
        (0 until 30).forEach { i ->
            val date = LocalDate.parse("2026-09-15").plusDays((i / 2 * 3).toLong())
            val amount = BigDecimal(listOf("10.00", "25.50", "10.00", "99.99", "3.20")[i % 5])
            val category = categories[i % 3]
            val account = accounts[i % 2]
            val id = createSpending(alice, category, account, amount = amount.toPlainString(), date = date.toString())
            rows += Row(id, date, amount, category, account)
        }
        val bobCategory = categoryIds(bob).getValue("Groceries")
        val bobAccount = createAccount(bob, "Mine")
        repeat(5) { createSpending(bob, bobCategory, bobAccount) }
    }

    private fun page(query: String): Map<String, Any> {
        var body = emptyMap<String, Any>()
        getJson(alice, "$base?$query")
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$")
            .value<Map<String, Any>> { body = it }
        return body
    }

    @Suppress("UNCHECKED_CAST")
    private fun ids(query: String): List<String> =
        (page(query)["items"] as List<Map<String, Any>>).map { it["id"] as String }

    private fun allPages(query: String): List<String> = (0 until 5).flatMap { ids("$query&page=$it&size=7") }

    @Test
    fun `the default page is 20 of the newest, with totals`() {
        val body = page("")

        assertThat(body["page"]).isEqualTo(0)
        assertThat(body["size"]).isEqualTo(20)
        assertThat(body["totalItems"]).isEqualTo(30)
        assertThat(body["totalPages"]).isEqualTo(2)
        val firstPage = ids("")
        assertThat(
            firstPage,
        ).containsExactlyInAnyOrderElementsOf(rows.sortedByDescending { it.date }.take(20).map { it.id })
        assertThat(
            firstPage.map { id ->
                rows.first { it.id == id }.date
            },
        ).isSortedAccordingTo(compareByDescending { it })
    }

    @ParameterizedTest
    @ValueSource(strings = ["date,desc", "date,asc", "amount,desc", "amount,asc"])
    fun `paging returns every spending exactly once in every sort order`(sort: String) {
        val seen = allPages("sort=$sort")

        assertThat(seen).hasSize(30).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(rows.map { it.id })
    }

    @Test
    fun `amount sorting orders by amount`() {
        val amounts = ids("sort=amount,desc&size=100").map { id -> rows.first { it.id == id }.amount }

        assertThat(amounts).isSortedAccordingTo(compareByDescending { it })
    }

    @Test
    fun `a date range includes both ends`() {
        val october = rows.filter { it.date.monthValue == 10 }.map { it.id }

        assertThat(ids("from=2026-10-01&to=2026-10-31&size=100")).containsExactlyInAnyOrderElementsOf(october)
        assertThat(ids("from=2026-10-01&size=100")).containsExactlyInAnyOrderElementsOf(october)
        val firstDay = rows.filter { it.date == LocalDate.parse("2026-09-15") }.map { it.id }
        assertThat(firstDay).hasSize(2)
        assertThat(ids("to=2026-09-15&size=100")).containsExactlyInAnyOrderElementsOf(firstDay)
        assertThat(ids("from=2026-09-15&to=2026-09-15")).containsExactlyInAnyOrderElementsOf(firstDay)
    }

    @Test
    fun `category and account filters match any of their values and combine with AND`() {
        val groceriesOrTaxi = rows.filter { it.category in setOf(groceries, taxi) }

        assertThat(ids("categoryId=$groceries&categoryId=$taxi&size=100"))
            .containsExactlyInAnyOrderElementsOf(groceriesOrTaxi.map { it.id })
        assertThat(ids("categoryId=$groceries&categoryId=$taxi&accountId=$amex&size=100"))
            .containsExactlyInAnyOrderElementsOf(groceriesOrTaxi.filter { it.account == amex }.map { it.id })
    }

    @Test
    fun `items carry the names of their category and account`() {
        @Suppress("UNCHECKED_CAST")
        val first = (page("size=1&sort=date,asc")["items"] as List<Map<String, Any>>).single()

        assertThat((first["category"] as Map<*, *>)["name"]).isEqualTo("Groceries")
        assertThat((first["account"] as Map<*, *>)["name"]).isEqualTo("Amex")
        assertThat((first["amount"] as Map<*, *>)["currency"]).isEqualTo("CAD")
    }

    @Test
    fun `nothing matching is an empty page, not an error`() {
        val body = page("categoryId=00000000-0000-0000-0000-000000000000")

        assertThat(body["items"] as List<*>).isEmpty()
        assertThat(body["totalItems"]).isEqualTo(0)
        getJson(tokenFor("auth0|newcomer"), base)
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.items")
            .isEmpty()
    }

    @Test
    fun `bad parameters are 422 naming the parameter`() {
        mapOf(
            "size=0" to "#/size",
            "size=101" to "#/size",
            "page=-1" to "#/page",
            "from=2026-11-01&to=2026-10-01" to "#/from",
            "sort=merchant,asc" to "#/sort",
            "from=2026-13-01" to "#/from",
        ).forEach { (query, pointer) ->
            getJson(alice, "$base?$query")
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors[0].pointer")
                .isEqualTo(pointer)
        }
    }

    @Test
    fun `nobody sees another user's spendings`() {
        assertThat(ids("size=100")).hasSize(30).containsExactlyInAnyOrderElementsOf(rows.map { it.id })
        var bobTotal = 0
        getJson(bob, base).expectBody().jsonPath("$.totalItems").value<Int> { bobTotal = it }
        assertThat(bobTotal).isEqualTo(5)
    }
}
