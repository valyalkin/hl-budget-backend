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
| `http://localhost:8080/api/v1/monthly-totals?year=2026` | `GET`: total and count for each of the 12 months, plus `today` |
| `http://localhost:8080/api/v1/category-totals?from=…&to=…` | `GET`: total per category with spending in the range |
| `http://localhost:8080/api/v1/merchants` | `GET`: past merchants, most used first, with their latest category and account |
| `http://localhost:8080/api/v1/spending-imports` | `POST`: 1–1000 spendings at once, all or nothing; `/{id}`: `GET` the import's receipt |
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

## Spending screens (feature 003)

- **Totals** come from the server, summed in Postgres on exact `numeric`: `monthly-totals` always
  returns 12 months (zeros included) and `today` in the app's time zone; `category-totals` returns
  only categories with spending, largest first. Totals use `MoneyTotal`, which may be zero.
- **Merchants** are derived from spendings, not stored: spellings equal ignoring case and spaces
  count as one merchant. `limit` is 1–1000 (default 500); `truncated` says whether more exist.
- **By name**: a spending may give `categoryName`/`accountName` instead of the id (exactly one of
  each pair). A name reuses the caller's item ignoring case or creates it in the same transaction;
  a refused spending creates nothing.
- **The last account cannot be deleted** (422 `details.reason = last-account`), checked after
  in use (409).

## CSV import (feature 004)

- **`POST /api/v1/spending-imports`** creates 1–1000 spendings in one transaction. Categories and
  accounts are given by name (`categoryName`, `accountName`); each distinct name, ignoring case
  and surrounding spaces, is found or created once. Rows may be in any month.
- **All or nothing**: every invalid field of every row is reported in one 422, its pointer naming
  the row (`#/spendings/3/date`), and nothing is created, not even new names.
- **Idempotency**: with an `Idempotency-Key`, repeating the same rows returns the first import
  (200, `Idempotent-Replayed: true`); the same key with other rows is 422
  `details.reason = idempotency-key-reused`.
- **Receipt**: migration V4 adds `spending_imports` (counts, first and last date, total, key and
  fingerprint), readable at `GET /api/v1/spending-imports/{id}` by its owner only. It stores
  nothing from the file, and the imported spendings stay ordinary spendings.
- The CSV file itself is parsed in the browser; this service only ever sees the reviewed rows.

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
