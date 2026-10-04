[Versión en español](README.md)

# Accounts and transfers API

A REST API built with Java 21 and Spring Boot 3.5 that models the basics of a bank: customers, accounts (savings and checking, in Argentine pesos and US dollars), account movements and transfers between accounts. It is a portfolio project and no bank uses it. It covers the kind of work banks, fintechs and consulting firms in Argentina usually ask for, with a focus on the parts that must not fail in this kind of system: a transfer must never run twice, two concurrent transfers must never overdraw an account, and errors must be easy to understand.

The code, API messages and comments are in Spanish (the target audience is Argentine). This page is a translation of the main README.

**Stack:** Java 21 · Spring Boot 3.5.16 · Spring Web · Spring Data JPA / Hibernate · PostgreSQL 16 · Flyway · Spring Security (JWT resource server) · Bean Validation · springdoc-openapi (Swagger UI) · JUnit 5 · Mockito · MockMvc · Maven · Docker.

![Swagger UI](docs/capturas/01-swagger-endpoints.png)

## What it does

- **Customers and users.** There are two roles. `CLIENTE` (customer) sees and operates only their own accounts. `OPERADOR` (bank employee) creates customers and accounts, records deposits, blocks accounts and can see everything. Passwords are stored with BCrypt.
- **Accounts.** An account is `CA` (savings) or `CC` (checking), in `ARS` or `USD`. Each one has a 22-digit CBU (the Argentine bank account number) generated with the real check-digit algorithm and a daily transfer limit. A checking account can also have an agreed overdraft.
- **Transfers** between accounts of this bank:
  - same currency only (a currency mismatch is rejected explicitly, with no conversion);
  - atomic: the debit, the credit, the transfer and both movements are all applied, or none of them is;
  - pessimistic locks taken in a fixed order, so there is no overdraft and no deadlock;
  - a mandatory `Idempotency-Key` header, so a retried request does not transfer twice;
  - a daily limit per account, with the day computed in Argentina time.
- **Movements.** The history is paginated and can be filtered by date. The **statement** for a period includes the opening balance, total credits, total debits and the closing balance.
- **Errors** follow RFC 7807 (`ProblemDetail`), with Spanish messages and a stable machine-readable `codigo`.
- **OpenAPI** at `/swagger-ui.html`.

### Endpoints

| Method | Path | Who | What |
|---|---|---|---|
| POST | `/api/auth/login` | public | Returns a JWT |
| GET | `/api/cuentas` | both | CLIENTE: own accounts. OPERADOR: all of them (`?clienteId=` to filter) |
| GET | `/api/cuentas/{id}` | both | Details (another customer's account returns 404) |
| POST | `/api/cuentas` | OPERADOR | Opens an account and generates its CBU |
| PATCH | `/api/cuentas/{id}/estado` | OPERADOR | `ACTIVA` / `BLOQUEADA` |
| POST | `/api/cuentas/{id}/depositos` | OPERADOR | Cash deposit at a branch |
| GET | `/api/cuentas/{id}/movimientos` | both | `?desde=YYYY-MM-DD&hasta=…&pagina=0&tamanio=20` |
| GET | `/api/cuentas/{id}/extracto` | both | `?desde=…&hasta=…` (defaults to the last 30 days) |
| POST | `/api/transferencias` | both | Requires the `Idempotency-Key` header |
| GET | `/api/transferencias/{id}` | both | CLIENTE: only if they own the source or destination account |
| POST | `/api/clientes` | OPERADOR | Creates a customer |
| GET | `/api/clientes` | OPERADOR | Lists customers |
| GET | `/api/clientes/{id}` | both | CLIENTE: only their own record |

Demo users (loaded only when `CARGAR_DATOS_DEMO=true` and the database is empty): `ana / ana123` and `bruno / bruno123` (CLIENTE), `operador / operador123` (OPERADOR).

## Design decisions

### Concurrency: pessimistic locks in a fixed order

A transfer reads the balance, checks it and updates it. If two transfers from the same account do that at the same time without protection, both see the same balance and both succeed (the classic *lost update*). This was verified: without the lock, the concurrency test approves 20 transfers when only 10 fit.

- `CuentaRepository.buscarParaActualizar` uses `@Lock(PESSIMISTIC_WRITE)`. On PostgreSQL, Hibernate issues `SELECT … FOR NO KEY UPDATE`: the row stays locked until commit, and any other transfer touching that account waits.
- **Lock order:** the account with the lower id is always locked first. If A→B and B→A run at the same time, both ask for the same row first, so one waits for the other and no deadlock happens. Without that ordering, the crossed-transfers test fails with PostgreSQL's `ERROR: deadlock detected` (also verified).
- **Why not optimistic locking (`@Version`)?** On a busy account (for example, one that receives many payments) optimistic locking causes many conflicts, and each one needs a retry. Pessimistic locking puts the operations in a queue. Transactions are short (a few queries), so the wait is small.
- A JPA detail: before locking, only the destination account's id is read (`buscarIdPorCbu`), not the entity. If the entity were already in the persistence context, the later `SELECT … FOR UPDATE` would hand back the cached instance with a stale balance.
- The daily limit is computed **while the source account is locked**, so the total sent that day is exact.
- As a last line of defence, the database has `CHECK (saldo >= -descubierto_autorizado)`.

### Idempotency (`Idempotency-Key`)

If a client sends a transfer and the connection drops before the response arrives, the client cannot tell whether the transfer happened. With this header it can retry safely:

- The key is stored in the `transferencia` table under `UNIQUE (usuario, idempotency_key)`, together with a SHA-256 hash of the request data.
- Same key with **the same data**: the original transfer comes back (same id) with `Idempotent-Replayed: true`, and no money moves.
- Same key with **different data**: `422 clave-idempotencia-reutilizada`.
- **Two identical requests at the same time:** the second one waits for the account lock. When it gets the lock, it finds the key already committed and returns the original. If the race happens some other way, the second request hits the UNIQUE constraint, its whole transaction (including the debit) is rolled back, and the service reads the winning transfer in a fresh query. That is why `TransferenciaService` is not transactional and delegates to `EjecutorDeTransferencias`, which is.
- Only successful transfers are recorded. A failed one (for example, insufficient funds) leaves no trace, so it can be retried with the same key.

### CBU check digits

A CBU has 22 digits in two blocks, and each block ends with a check digit:

| Block | Content | Weights |
|---|---|---|
| 1 (8 digits) | bank (3) + branch (4) + check digit | 7, 1, 3, 9, 7, 1, 3 |
| 2 (14 digits) | account number (13) + check digit | 3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3 |

In each block, every digit is multiplied by its weight and the results are added up. The check digit is `(10 - sum % 10) % 10`. The algorithm is in [`Cbu.java`](src/main/java/ar/cuentas/dominio/Cbu.java) and is tested against a real CBU published as an example (`2850590940090418135201`). Generated CBUs use a fictitious bank code (`999`) and branch `0001`, and the account number comes from a PostgreSQL sequence. A destination CBU with a wrong check digit is rejected with 400 before the database is touched.

### Errors

Every error response is `application/problem+json`, for example:

```json
{
  "type": "urn:cuentas-api:error:monedas-distintas",
  "title": "Operación rechazada",
  "status": 422,
  "detail": "No se puede transferir entre cuentas de distinta moneda (ARS a USD). Esta API no hace conversión de moneda.",
  "instance": "/api/transferencias",
  "codigo": "monedas-distintas"
}
```

| Status | When |
|---|---|
| 400 | Validation (with a per-field `errores` list), unreadable JSON, missing `Idempotency-Key`, invalid dates |
| 401 | No token, expired or tampered token, wrong credentials |
| 403 | The role is not allowed (for example, a CLIENTE trying to open an account) |
| 404 | Does not exist, **or belongs to another customer**: both cases look the same, so the API does not reveal which ids exist |
| 409 | Uniqueness conflict in the database |
| 422 | Business rule: `saldo-insuficiente` (insufficient funds), `monedas-distintas`, `limite-diario-excedido`, `cuenta-bloqueada`, `misma-cuenta`, `clave-idempotencia-reutilizada`, `dni-duplicado`… |
| 503 | The database aborted because of a lock problem (should not happen; retry with the same key) |

401 and 403 happen in the security filter chain, before any controller runs, so they have their own handler ([`ProblemaSeguridadHandler`](src/main/java/ar/cuentas/seguridad/ProblemaSeguridadHandler.java)) that writes the same format.

### Security

- JWT signed with HS256. Instead of a hand-written filter, the API uses Spring Security's *resource server* support, which validates the signature, expiry and issuer. The token carries `sub`, `rol` and `clienteId`.
- **The secret comes from the `JWT_SECRET` environment variable.** `application.yml` has a default that starts with `solo-desarrollo-local…` ("local development only"), meant only for running on your own machine. When that default is in use, the app logs a warning. A secret shorter than 32 bytes stops the app from starting. In `docker-compose.yml` the variable is required.
- Passwords use BCrypt. Login runs the BCrypt comparison even when the user does not exist, so response time does not reveal which users exist.
- `@PreAuthorize` handles the OPERADOR-only actions. Ownership checks live in the services.
- The API is stateless and uses no cookies, so CSRF protection is disabled.

### Other decisions

- Money is `BigDecimal` / `NUMERIC(19,2)`, never `double`. Amounts can have at most 2 decimals.
- Timestamps are stored in UTC (`TIMESTAMPTZ`). The "day" for the daily limit and the `desde`/`hasta` filters use `America/Argentina/Buenos_Aires`. A `Clock` is injected so tests can fix the date.
- Balance rules live in the `Cuenta` entity (`debitar`, `acreditar`), so they can be tested without a database.
- DTOs are `record`s and the mapping is written by hand; with this few classes there was no need for MapStruct.
- `spring.jpa.open-in-view=false` and `ddl-auto=validate`: only Flyway changes the schema.

### Diagrams

Layers and model ([source](docs/diagramas/arquitectura.puml)):

![Architecture](docs/diagramas/arquitectura.png)

Transfer sequence ([source](docs/diagramas/secuencia-transferencia.puml)):

![Sequence](docs/diagramas/secuencia-transferencia.png)

## Structure

```
src/main/java/ar/cuentas/
├── web/            REST controllers, DTOs (records), CBU validation
├── servicio/       use cases: TransferenciaService (idempotency),
│                   EjecutorDeTransferencias (@Transactional, locks), accounts, movements
├── repositorio/    Spring Data JPA (including SELECT ... FOR UPDATE)
├── dominio/        JPA entities with rules (Cuenta, Movimiento, Transferencia), Cbu
├── seguridad/      JWT, Spring Security config, 401/403 as ProblemDetail
├── error/          @RestControllerAdvice and exceptions
└── config/         clock, OpenAPI, properties, demo data
src/main/resources/db/migration/   Flyway migrations
src/test/java/ar/cuentas/
├── dominio/        unit tests (CBU, Cuenta rules)
├── servicio/       Mockito unit tests
└── integracion/    MockMvc and real HTTP against PostgreSQL (including concurrency)
docs/               diagrams (PlantUML) and screenshots
ejemplos.http       ready-made requests for IntelliJ / VS Code REST Client
```

## How to run

### With a local PostgreSQL

You need Java 21, Maven 3.9+ and PostgreSQL 16.

```bash
createdb cuentas                      # plus cuentas_test for the tests
export DB_URL=jdbc:postgresql://localhost:5432/cuentas DB_USER=postgres DB_PASSWORD=postgres
export JWT_SECRET="$(openssl rand -base64 48)"   # optional locally; otherwise the dev default is used
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html (use "Authorize" with the token from the login)

The demo CBUs are: Ana ARS `9990001800000000010001`, Ana USD `9990001800000000010018`, Bruno ARS `9990001800000000010025` and Bruno USD `9990001800000000010032`.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"ana","password":"ana123"}' | jq -r .token)

curl -i -X POST localhost:8080/api/transferencias \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7f1c0e7a-alquiler-octubre' \
  -d '{"cuentaOrigenId":1,"cbuDestino":"9990001800000000010025","importe":85000,"concepto":"Alquiler octubre"}'
# HTTP/1.1 201 · Location: /api/transferencias/1 · Idempotent-Replayed: false
# Running the same command again returns the same transfer with Idempotent-Replayed: true
```

[`ejemplos.http`](ejemplos.http) has more requests, including the error cases.

### With Docker Compose

```bash
cp .env.example .env      # set JWT_SECRET
docker compose up --build
```

This starts `postgres:16` and the API on port 8080. **Note:** the `Dockerfile` and `docker-compose.yml` are written but were never tested, because Docker was not available where the project was built. See the table below.

## Tests

The integration tests run against a **real PostgreSQL**, not H2, because row locks (`FOR UPDATE`), constraints and timestamp handling must behave as they do in production. Testcontainers is not used because the build environment had no Docker. Instead, the `test` profile points to a local database:

```bash
createdb cuentas_test
mvn test
# another database: TEST_DB_URL=jdbc:postgresql://host:5432/db TEST_DB_USER=... TEST_DB_PASSWORD=... mvn test
```

Each integration test truncates the tables (`TRUNCATE … RESTART IDENTITY`) and loads its own data. Latest run:

```
Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Class | Kind | What it covers |
|---|---|---|
| `CbuTest` (11) | unit | Check-digit algorithm, against a real CBU and with invalid cases |
| `CuentaTest` (6) | unit | Debit, insufficient funds, checking-account overdraft, blocked account, no overdraft on savings |
| `EjecutorDeTransferenciasTest` (7) | Mockito | Lock order, currency mismatch, daily limit (Argentina time), account ownership, movements |
| `TransferenciaServiceTest` (6) | Mockito | Idempotent replay, key reused with different data, race settled by UNIQUE, hash |
| `AuthIntegrationTest` (4) | MockMvc + PG | Login, wrong credentials, no token, tampered token |
| `CuentaIntegrationTest` (8) | MockMvc + PG | Role-based visibility, 404 for other customers' accounts, creation with a valid CBU, validation, deposits, blocking |
| `TransferenciaIntegrationTest` (12) | MockMvc + PG | Happy path, insufficient funds with no side effects, overdraft, idempotency, daily limit, invalid CBU… |
| `MovimientoIntegrationTest` (4) | MockMvc + PG | Pagination, date filter in Argentina time, statement, invalid parameters |
| `ConcurrenciaIntegrationTest` (3) | real HTTP + PG | **20 parallel transfers** from an account with funds for 10: exactly 10 succeed and the balance ends at 0. **Crossed A↔B transfers** with no deadlock. **10 simultaneous retries** with the same key: one transfer |

## What is tested and what isn't

| Topic | Status |
|---|---|
| Unit and integration tests (61) against a local PostgreSQL 16 | ✅ Run, all passing (`mvn test`) |
| The concurrency test really catches bugs | ✅ Checked by hand: without `@Lock` it fails (20 approved instead of 10); without the lock order, PostgreSQL reports `deadlock detected` |
| The app running for real (`java -jar`): login, transfer, idempotent retry, 422 error and movements via curl | ✅ Done by hand |
| Swagger UI | ✅ Rendered and used through Playwright (the screenshots in `docs/capturas/`) |
| `Dockerfile` and `docker-compose.yml` | ⚠️ Written, **never built or started** (no Docker available) |
| GitHub Actions workflow | ⚠️ Written, **never run** |
| JWT expiry | ⚠️ Enforced by Spring Security, but there is no dedicated test (there is one for a tampered token) |
| Several API instances against the same database | ⚠️ The lock lives in the database, so it should work, but it was not tested |
| Performance and load | ❌ Not measured |
| Transfers to other banks, currency conversion, token refresh or revocation, login rate limiting | ❌ Out of scope, not implemented |

## Screenshots

| Transfer executed from Swagger | Business error as ProblemDetail |
|---|---|
| ![Transfer](docs/capturas/02-swagger-transferencia.png) | ![Error](docs/capturas/03-swagger-error-problemdetail.png) |
