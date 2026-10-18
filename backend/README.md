# Backend — Kotlin + Spring Boot

Hexagonal (ports & adapters) architecture. See the root [`AGENTS.md`](../AGENTS.md)
for the full convention set.

## Run

```bash
./gradlew bootRun     # http://localhost:8080
./gradlew test        # tests
./gradlew build       # compile + test + jar
```

Requires a JDK 21+ on the machine; the Gradle wrapper handles the rest.

## API

| Method | Path                     | Description                            |
|--------|--------------------------|----------------------------------------|
| GET    | `/api/tournaments`       | list the caller's tournaments          |
| GET    | `/api/tournaments/{id}`  | get one tournament (404 for foreign ids) |
| POST   | `/api/tournaments`       | create a tournament                    |

All tournament endpoints require a valid admin session. New tournaments start in
status `setup` (the machine is `setup → active → completed`, final).

Example:

```bash
curl -X POST localhost:8080/api/tournaments \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spring Classic","description":"annual invitational","maxTeams":16,"settings":{"allowGrandTichu":true}}'
```

### Auth (admin accounts)

| Method | Path                 | Description                                        |
|--------|----------------------|----------------------------------------------------|
| POST   | `/api/auth/register` | create an admin account with email/password        |
| POST   | `/api/auth/login`    | exchange credentials for an httpOnly session cookie |
| POST   | `/api/auth/refresh`  | rotate a valid session cookie                      |
| POST   | `/api/auth/logout`   | invalidate the session and clear the cookie        |

Sessions use the `admin_session` httpOnly `Secure` `SameSite=Lax` cookie; the raw
token is never returned in a response body. Error responses are RFC 7807 problem
JSON carrying a stable `code` (`EMAIL_ALREADY_REGISTERED`, `INVALID_CREDENTIALS`,
`UNAUTHENTICATED`, `INVALID_INPUT`, `NOT_FOUND`). Cookie lifetime and the `Secure`
flag are configured under `app.auth` in `application.yml`.

```bash
# register, then log in and keep the session cookie in a jar
curl -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"organizer@example.com","password":"s3cret-pw"}'

curl -c cookies.txt -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"organizer@example.com","password":"s3cret-pw"}'

curl -b cookies.txt -c cookies.txt -X POST localhost:8080/api/auth/refresh
```

## Layers

- `domain` — `Task`, `TaskStatus`, `TaskId`, and the `TaskRepository`/`IdGenerator`
  ports. No Spring.
- `application/api` — inbound ports: `CreateTask`, `ListTasks`, `GetTask`,
  `UpdateTask`, `DeleteTask`, `RegisterAdmin`, `LoginAdmin`, `RefreshSession`,
  `LogoutAdmin`.
- `application/usecase` — implementations + `UseCaseConfiguration`.
- `infra/persistence` — `JpaTaskRepository` (+ `TaskEntity` and the Spring Data
  repository it wraps), `UuidIdGenerator` + `PersistenceConfiguration`.
  `InMemoryTaskRepository` remains as a fast fake for use-case unit tests.
- `infra/security` — `BcryptPasswordHasher`, `SecureSessionTokens` +
  `SecurityConfiguration` (the crypto ports).
- `infra/rest` — `TaskController`, `AuthController`, DTOs, the session-cookie
  factory, error advice + `RestConfiguration`.
- `main` — `Application` composition root.

The production adapter is JPA over PostgreSQL with the schema managed by Flyway
(`src/main/resources/db/migration`). To swap the storage technology again, write
a new adapter implementing `TaskRepository` and change the bean in
`PersistenceConfiguration`. Nothing else changes.
