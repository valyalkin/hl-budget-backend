package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.UUID

/**
 * SC-003, FR-002: another user's items do not exist for the caller, on every operation, and the
 * answer is the same as for an id that was never created.
 */
class OwnershipIT : AbstractIntegrationTest() {
    private lateinit var account: String
    private lateinit var custom: String
    private lateinit var default: String
    private lateinit var spending: String
    private lateinit var before: List<Map<String, Any?>>

    private fun snapshot(): List<Map<String, Any?>> =
        listOf("accounts", "categories", "spendings").flatMap { table ->
            jdbcTemplate.queryForList(
                "SELECT * FROM $table WHERE user_id = (SELECT id FROM users WHERE auth0_subject = ?) ORDER BY id",
                ALICE,
            )
        }

    @BeforeEach
    fun aliceOwnsOneOfEach() {
        default = categoryIds(alice).getValue("Groceries")
        account = createAccount(alice, "Amex")
        sendJson(alice, "POST", "/api/v1/categories", """{"name":"Coffee"}""")
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { custom = it }
        spending = createSpending(alice, custom, account)
        before = snapshot()
    }

    private fun notFoundBody(
        method: String,
        uri: String,
        json: String? = null,
    ): Map<String, Any?> {
        val response =
            when {
                method == "GET" -> getJson(bob, uri)
                method == "DELETE" -> delete(bob, uri)
                else -> sendJson(bob, method, uri, json ?: "{}")
            }
        var body = emptyMap<String, Any?>()
        response
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("$")
            .value<Map<String, Any?>> { body = it }
        return body - setOf("traceId", "timestamp", "instance", "detail", "details")
    }

    @Test
    fun `every path operation on another user's item is a plain 404`() {
        val bobCategory = categoryIds(bob).getValue("Taxi")
        val bobAccount = createAccount(bob, "Cash")
        val rename = """{"name":"Mine"}"""
        val replace = spendingJson(bobCategory, bobAccount)
        val unknown = UUID.randomUUID()

        val cases =
            listOf(
                Triple("GET", "/api/v1/accounts/{id}", null),
                Triple("PUT", "/api/v1/accounts/{id}", rename),
                Triple("DELETE", "/api/v1/accounts/{id}", null),
                Triple("GET", "/api/v1/spendings/{id}", null),
                Triple("PUT", "/api/v1/spendings/{id}", replace),
                Triple("DELETE", "/api/v1/spendings/{id}", null),
            )
        val ids = mapOf("/api/v1/accounts" to account, "/api/v1/spendings" to spending)
        cases.forEach { (method, template, json) ->
            val id = ids.getValue(template.substringBefore("/{id}"))
            val theirs = notFoundBody(method, template.replace("{id}", id), json)
            val nobodys = notFoundBody(method, template.replace("{id}", unknown.toString()), json)
            assertThat(theirs).describedAs("$method $template").isEqualTo(nobodys)
        }
        listOf(custom, default).forEach { category ->
            notFoundBody("GET", "/api/v1/categories/$category")
            notFoundBody("PUT", "/api/v1/categories/$category", rename)
            notFoundBody("DELETE", "/api/v1/categories/$category")
        }

        assertThat(snapshot()).isEqualTo(before)
    }

    @Test
    fun `another user's account or category cannot be referenced on create or update`() {
        val bobCategory = categoryIds(bob).getValue("Taxi")
        val bobAccount = createAccount(bob, "Cash")
        val bobSpending = createSpending(bob, bobCategory, bobAccount)

        listOf("POST" to "/api/v1/spendings", "PUT" to "/api/v1/spendings/$bobSpending").forEach { (method, uri) ->
            sendJson(bob, method, uri, spendingJson(custom, account))
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors[*].pointer")
                .value<List<String>> { assertThat(it).containsExactly("#/categoryId", "#/accountId") }
        }
        assertThat(snapshot()).isEqualTo(before)
    }

    @Test
    fun `lists never contain another user's items`() {
        fun ids(uri: String): List<String> {
            var ids = emptyList<String>()
            getJson(bob, uri).expectBody().jsonPath("$.items[*].id").value<List<String>> { ids = it }
            return ids
        }

        assertThat(ids("/api/v1/accounts")).isEmpty()
        assertThat(ids("/api/v1/spendings?size=100")).isEmpty()
        assertThat(ids("/api/v1/categories")).hasSize(17).doesNotContain(custom, default)
        assertThat(ids("/api/v1/spendings?accountId=$account&categoryId=$custom")).isEmpty()
    }

    private fun importJson(account: String) =
        """{"spendings":[{"date":"2026-10-20","merchant":"Corner Bakery",""" +
            """"amount":{"amount":"12.40","currency":"CAD"},"categoryName":"Groceries","accountName":"$account"}]}"""

    private fun importAs(
        token: String,
        key: String,
        account: String,
    ): String {
        var id = ""
        client
            .post()
            .uri("/api/v1/spending-imports")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .body(importJson(account))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody()
            .jsonPath("$.id")
            .value<String> { id = it }
        return id
    }

    @Test
    fun `another user's import does not exist, and names and keys are per user`() {
        val alicesImport = importAs(alice, "shared-key", "Amex")

        getJson(bob, "/api/v1/spending-imports/$alicesImport").expectStatus().isNotFound()

        // Bob's "amex" is his own new account, and Alice's key means nothing to him.
        importAs(bob, "shared-key", "amex")
        assertThat(
            count(
                "SELECT count(*) FROM accounts WHERE lower(name) = 'amex' AND user_id = " +
                    "(SELECT id FROM users WHERE auth0_subject = ?)",
                BOB,
            ),
        ).isEqualTo(1)
        assertThat(count("SELECT count(*) FROM accounts WHERE lower(name) = 'amex'")).isEqualTo(2)
        assertThat(count("SELECT count(*) FROM spending_imports")).isEqualTo(2)
    }
}
