# hl-budget-backend

The HL Budget API: Kotlin 2.3 on Java 21, Spring Boot 4.1, PostgreSQL 17, Flyway. It validates an
Auth0 access token on every request and owns the data and the HTTP contract
(`contracts/api.yaml`). The web app is [hl-budget-frontend](https://github.com/valyalkin/hl-budget-frontend).

Feature specs live in the `hl-budget-spec` repository (`specs/001-platform-foundation/`,
`specs/002-spending-tracking/`).

## Prerequisites

- JDK 21 (Gradle's toolchain picks it up), Docker (local Postgres and Testcontainers).
- An Auth0 **dev/test** tenant set up per the spec's quickstart §1: an API (RS256, identifier =
  audience), a Regular Web Application, sign-ups disabled, and the Post-Login Action in
  [`auth0/post-login-profile-claims.js`](auth0/post-login-profile-claims.js) attached to the
  Login flow (deploy steps are in the file's header).

## Run locally

```sh
cp .env.example .env.local        # fill HLBUDGET_AUTH_* from the dev tenant
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile starts Postgres 17.7 from `compose.yaml` (random host port, wired in
automatically) and reads `.env.local`. A missing `HLBUDGET_AUTH_*` value stops startup with a
message naming it. The local profile defaults the currency to `CAD` and the time zone to
`America/Toronto`; everywhere else they are required (see Configuration).

| URL | What |
|---|---|
| `http://localhost:8080/healthz` | Liveness, `{"status":"UP"}` |
| `http://localhost:8080/readyz` | Readiness; 503 while Postgres is unreachable |
| `http://localhost:8080/api/v1/users/me` | `PUT` with a bearer token: create or refresh your record |
| `http://localhost:8080/api/v1/accounts` | `GET`, `POST`; `/{id}`: `GET`, `PUT` (rename), `DELETE` |
| `http://localhost:8080/api/v1/categories` | `GET` (the 17 defaults + your own), `POST`; `/{id}`: `GET`, `PUT`, `DELETE` |
| `http://localhost:8080/api/v1/spendings` | `GET` (paged, filtered, sorted), `POST`; `/{id}`: `GET`, `PUT`, `DELETE` |
| `http://localhost:8080/api/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/api/v3/api-docs.yaml` | The OpenAPI document |

## Quality gate

```sh
./gradlew check     # ktlint, detekt, unit + Testcontainers integration tests, Kover 80/75 and 75 per package
```

Docker must be running for the integration tests. CI runs the same gate, gitleaks, and checks
that `contracts/api.yaml` is up to date.

## The API contract

`OpenApiExportIT` writes the running document to `contracts/api.yaml` on every test run. Commit
it with the change that caused it. The frontend copies this file and generates its types from it,
so a controller change ships as: code + regenerated `contracts/api.yaml` here, then the copy and
`pnpm generate:api` in the frontend.

## Spending tracking (feature 002)

- **Every user starts with 17 default categories** (Rent, Groceries, … Taxi). They are created
  on the user's first budgeting call, exactly once. Defaults cannot be renamed or deleted (422,
  `details.reason = default-category`).
- **Names** of accounts and categories are trimmed, 1–50 characters, and unique per user
  ignoring case (409 `duplicate-name`).
- **An account or category that spendings use cannot be deleted** (409 `in-use`, with
  `details.spendingCount`). Every account and category carries a live `spendingCount`.
- **Money** is `{ "amount": "84.37", "currency": "CAD" }`. The amount is a decimal string, greater
  than 0, at most 99,999,999.99, and a JSON number is refused. The currency must be the configured
  one.
- **Dates** cannot be after today in the configured time zone.
- **Retries are safe**: send `Idempotency-Key` on `POST /api/v1/spendings`. The same key and body
  return the first spending with 200 and `Idempotent-Replayed: true`; the same key with a
  different body is 422 `idempotency-key-reused`.
- **Status codes**: 400 means the request cannot be read (not JSON, or a path id that is not a
  UUID). 422 means fix these fields, and every one is listed in `errors` with a JSON Pointer and a
  `code`. 404 is also the answer for another user's item.

## Configuration

Every setting is an environment variable; see [`.env.example`](.env.example) (purpose, secret or
not, required or not). `HLBUDGET_MONEY_CURRENCY` (ISO 4217, e.g. `CAD`) and `HLBUDGET_TIMEZONE`
(IANA, e.g. `America/Toronto`) are required: startup fails, naming the variable, when either is
missing or invalid. Changing the currency later does not convert stored amounts. In the cluster, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` come from Vault; nothing secret is in Git, the image or the chart.

## Container

`Dockerfile` builds a jar on Temurin 21 JDK and runs it on Temurin 21 JRE as UID 10001 with a
read-only root filesystem; `/tmp` is the only writable directory. Port 8080, stops cleanly on
SIGTERM (graceful shutdown, 20 s per phase).
