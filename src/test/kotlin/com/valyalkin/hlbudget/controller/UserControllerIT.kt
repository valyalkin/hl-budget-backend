package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.support.AbstractIntegrationTest
import com.valyalkin.hlbudget.support.JwtTestTokens
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class UserControllerIT(
    @param:org.springframework.beans.factory.annotation.Autowired private val mapper: ObjectMapper,
) : AbstractIntegrationTest() {
    private fun put(token: String = JwtTestTokens.token()): RestTestClient.ResponseSpec =
        client
            .put()
            .uri("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))
            .exchange()

    private fun RestTestClient.ResponseSpec.json(): JsonNode =
        mapper.readTree(expectBody(String::class.java).returnResult().responseBody)

    @Test
    fun `the first call creates the record, returns 201 with Location and exactly the profile fields`() {
        val body =
            put()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "/api/v1/users/me")
                .json()

        assertThat(body.propertyNames()).containsExactlyInAnyOrder(
            "id",
            "displayName",
            "email",
            "avatarUrl",
            "firstSeenAt",
            "lastSeenAt",
        )
        assertThat(body["displayName"].asString()).isEqualTo("Alex Example")
        assertThat(body["email"].asString()).isEqualTo("alex@example.com")
        assertThat(body["avatarUrl"].asString()).isEqualTo("https://lh3.googleusercontent.com/a/example")
        assertThat(body["firstSeenAt"].asString()).isEqualTo(body["lastSeenAt"].asString()).endsWith("Z")
        assertThat(userCount()).isEqualTo(1)
    }

    @Test
    fun `a later call returns 200 for the same record, moving only lastSeenAt`() {
        val first = put().expectStatus().isCreated().json()
        val second =
            put()
                .expectStatus()
                .isOk()
                .expectHeader()
                .doesNotExist(HttpHeaders.LOCATION)
                .json()

        assertThat(second["id"]).isEqualTo(first["id"])
        assertThat(second["firstSeenAt"]).isEqualTo(first["firstSeenAt"])
        assertThat(
            Instant.parse(second["lastSeenAt"].asString()),
        ).isAfter(Instant.parse(first["lastSeenAt"].asString()))
        assertThat(userCount()).isEqualTo(1)
    }

    @Test
    fun `a changed name and email at the identity provider update the same row`() {
        val first = put().json()
        val updated =
            put(
                JwtTestTokens.token(
                    profile = mapOf("name" to "Alex Renamed", "email" to "new@example.com", "picture" to null),
                ),
            ).expectStatus()
                .isOk()
                .json()

        assertThat(updated["id"]).isEqualTo(first["id"])
        assertThat(updated["displayName"].asString()).isEqualTo("Alex Renamed")
        assertThat(updated["email"].asString()).isEqualTo("new@example.com")
        assertThat(updated["avatarUrl"].isNull).isTrue()
    }

    @Test
    fun `a token without name or picture claims gives null fields, serialised as null`() {
        val body =
            put(JwtTestTokens.token(profile = mapOf("email" to "only@example.com")))
                .expectStatus()
                .isCreated()
                .json()

        assertThat(body.has("displayName")).isTrue()
        assertThat(body["displayName"].isNull).isTrue()
        assertThat(body["avatarUrl"].isNull).isTrue()
        assertThat(body["email"].asString()).isEqualTo("only@example.com")
    }

    @Test
    fun `an identifier in the query string is ignored - identity comes from the token only`() {
        put().expectStatus().isCreated()

        client
            .put()
            .uri("/api/v1/users/me?sub=auth0|someone-else")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .exchange()
            .expectStatus()
            .isOk()

        assertThat(userCount("auth0|someone-else")).isZero()
    }
}
