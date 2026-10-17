# Fullstack Template

A generic fullstack monorepo template demonstrating a clean, layered architecture
across a Kotlin/Spring Boot backend and a React/TanStack frontend.

It implements a small **Task tracker** example domain (create / list / update /
delete tasks, each with a status) end to end, so the architecture is exercised by
real code rather than described in the abstract. Swap the `Task` domain for your
own and the structure carries over.

## Layout

```
fullstack-template/
├── backend/          # Kotlin + Spring Boot (Gradle), hexagonal architecture
└── frontend/         # React 19 + Vite + TanStack (pnpm workspace)
```

See [`AGENTS.md`](./AGENTS.md) for the architecture conventions, and the README in
each subproject for how to run it.

## Quick start

### Backend

```bash
cd backend
./gradlew bootRun        # starts http://localhost:8080
./gradlew test           # run tests
```

### Frontend

```bash
cd frontend
pnpm install
pnpm dev                 # starts http://localhost:3000
pnpm test                # run unit tests
```

The frontend expects the backend on `http://localhost:8080` (configurable via
`VITE_API_URL`). CORS for `http://localhost:3000` is enabled in the backend by
default.

## What this template demonstrates

**Backend (hexagonal / ports & adapters):**
- `domain` — entities and port interfaces, framework-free.
- `application/api` — inbound ports (use-case interfaces).
- `application/usecase` — use-case implementations (commands & queries).
- `infra` — adapters (REST controllers, persistence) and all Spring wiring.
- All beans wired explicitly via `@Configuration` + `@Bean` — no `@Service`,
  `@Repository`, `@Component`, or `@Autowired` on business classes.

**Frontend (layered, feature-oriented):**
- `*-api` layer — axios client, repository, DTOs (zod), and mappers DTO↔domain.
- `domain` — plain domain models, framework-free.
- `vocabulary` — enums / shared value types.
- `features` — feature folders (components, hooks, queries).
- `shared` — cross-cutting UI, stores (Zustand), query config, utils.
- `packages/ui` — a reusable component library consumed by the app.
- TanStack Router + TanStack Query, react-hook-form + zod, Tailwind CSS.
