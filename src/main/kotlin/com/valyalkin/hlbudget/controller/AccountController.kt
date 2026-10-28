package com.valyalkin.hlbudget.controller

import com.valyalkin.hlbudget.config.OpenApiBudgetComponents
import com.valyalkin.hlbudget.config.OpenApiConfig
import com.valyalkin.hlbudget.dto.NameRequest
import com.valyalkin.hlbudget.dto.account.AccountListResponse
import com.valyalkin.hlbudget.dto.account.AccountResponse
import com.valyalkin.hlbudget.dto.account.toListResponse
import com.valyalkin.hlbudget.dto.account.toResponse
import com.valyalkin.hlbudget.dto.toCommand
import com.valyalkin.hlbudget.model.VerifiedIdentity
import com.valyalkin.hlbudget.service.AccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

/**
 * The caller's accounts (US2). Returns ResponseEntity only on create, where the 201 owes a
 * `Location` header.
 */
@RestController
@RequestMapping(AccountController.BASE_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = OpenApiConfig.TAG_ACCOUNTS)
class AccountController(
    private val accounts: AccountService,
) {
    @GetMapping
    @Operation(
        operationId = "listAccounts",
        summary = "List all of the caller's accounts, sorted by name, with spending counts",
        description = "Not paged: a person has tens of accounts. A new user has none.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "All accounts",
        content = [Content(mediaType = JSON, schema = Schema(implementation = AccountListResponse::class))],
    )
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun listAccounts(
        @Parameter(hidden = true) identity: VerifiedIdentity,
    ): AccountListResponse = accounts.list(identity).toListResponse()

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "createAccount", summary = "Create an account")
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        headers = [Header(name = "Location", ref = OpenApiBudgetComponents.LOCATION_HEADER_REF)],
        content = [Content(mediaType = JSON, schema = Schema(implementation = AccountResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.DUPLICATE_NAME)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun createAccount(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @Valid @RequestBody request: NameRequest,
    ): ResponseEntity<AccountResponse> {
        val account = accounts.create(identity, request.toCommand())
        return ResponseEntity.created(URI.create("$BASE_PATH/${account.id}")).body(account.toResponse())
    }

    @GetMapping("/{accountId}")
    @Operation(operationId = "getAccount", summary = "Fetch one of the caller's accounts")
    @ApiResponse(
        responseCode = "200",
        description = "The account",
        content = [Content(mediaType = JSON, schema = Schema(implementation = AccountResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun getAccount(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable accountId: UUID,
    ): AccountResponse = accounts.get(identity, accountId).toResponse()

    @PutMapping("/{accountId}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "renameAccount", summary = "Rename an account (its name is its only field)")
    @ApiResponse(
        responseCode = "200",
        description = "Renamed",
        content = [Content(mediaType = JSON, schema = Schema(implementation = AccountResponse::class))],
    )
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.DUPLICATE_NAME_OR_CONCURRENT)
    @ApiResponse(responseCode = "422", ref = OpenApiBudgetComponents.VALIDATION_FAILED)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun renameAccount(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable accountId: UUID,
        @Valid @RequestBody request: NameRequest,
    ): AccountResponse = accounts.rename(identity, accountId, request.toCommand()).toResponse()

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAccount", summary = "Delete an account that no spending uses")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "400", ref = OpenApiBudgetComponents.BAD_REQUEST)
    @ApiResponse(responseCode = "401", ref = UNAUTHENTICATED)
    @ApiResponse(responseCode = "404", ref = OpenApiBudgetComponents.NOT_FOUND)
    @ApiResponse(responseCode = "409", ref = OpenApiBudgetComponents.IN_USE)
    @ApiResponse(responseCode = "500", ref = INTERNAL)
    @ApiResponse(responseCode = "503", ref = UNAVAILABLE)
    fun deleteAccount(
        @Parameter(hidden = true) identity: VerifiedIdentity,
        @PathVariable accountId: UUID,
    ) = accounts.delete(identity, accountId)

    companion object {
        const val BASE_PATH: String = "/api/v1/accounts"
        private const val JSON = MediaType.APPLICATION_JSON_VALUE
        private const val UNAUTHENTICATED = "#/components/responses/Unauthenticated"
        private const val INTERNAL = "#/components/responses/InternalError"
        private const val UNAVAILABLE = "#/components/responses/ServiceUnavailable"
    }
}
