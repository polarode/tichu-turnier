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

| Method | Path             | Description        |
|--------|------------------|--------------------|
| GET    | `/api/tasks`     | list tasks         |
| GET    | `/api/tasks/{id}`| get one task       |
| POST   | `/api/tasks`     | create a task      |
| PUT    | `/api/tasks/{id}`| update a task      |
| DELETE | `/api/tasks/{id}`| delete a task      |

Example:

```bash
curl -X POST localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Write docs","description":"for the template"}'
```

## Layers

- `domain` — `Task`, `TaskStatus`, `TaskId`, and the `TaskRepository`/`IdGenerator`
  ports. No Spring.
- `application/api` — inbound ports: `CreateTask`, `ListTasks`, `GetTask`,
  `UpdateTask`, `DeleteTask`.
- `application/usecase` — implementations + `UseCaseConfiguration`.
- `infra/persistence` — `JpaTaskRepository` (+ `TaskEntity` and the Spring Data
  repository it wraps), `UuidIdGenerator` + `PersistenceConfiguration`.
  `InMemoryTaskRepository` remains as a fast fake for use-case unit tests.
- `infra/rest` — `TaskController`, DTOs, error advice + `RestConfiguration`.
- `main` — `Application` composition root.

The production adapter is JPA over PostgreSQL with the schema managed by Flyway
(`src/main/resources/db/migration`). To swap the storage technology again, write
a new adapter implementing `TaskRepository` and change the bean in
`PersistenceConfiguration`. Nothing else changes.
