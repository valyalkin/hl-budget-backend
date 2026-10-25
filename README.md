# hl-budget-backend

The HL Budget API: Kotlin 2.3 on Java 21, Spring Boot 4.1, PostgreSQL 17, Flyway. It validates an
Auth0 access token on every request and owns the data and the HTTP contract
(`contracts/api.yaml`). The web app is [hl-budget-frontend](https://github.com/valyalkin/hl-budget-frontend).

Feature specs live in the `hl-budget-spec` repository (`specs/001-platform-foundation/`).

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
message naming it.

| URL | What |
|---|---|
| `http://localhost:8080/healthz` | Liveness, `{"status":"UP"}` |
| `http://localhost:8080/readyz` | Readiness; 503 while Postgres is unreachable |
| `http://localhost:8080/api/v1/users/me` | `PUT` with a bearer token: create or refresh your record |
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

## Configuration

Every setting is an environment variable; see [`.env.example`](.env.example) (purpose, secret or
not, required or not). In the cluster, `SPRING_DATASOURCE_USERNAME` and
`SPRING_DATASOURCE_PASSWORD` come from Vault; nothing secret is in Git, the image or the chart.

## Container

`Dockerfile` builds a jar on Temurin 21 JDK and runs it on Temurin 21 JRE as UID 10001 with a
read-only root filesystem; `/tmp` is the only writable directory. Port 8080, stops cleanly on
SIGTERM (graceful shutdown, 20 s per phase).
