# Architecture & conventions

This is a two-app monorepo. Read the relevant section before editing an area.

## Plan & tickets

- **Implementation plan**: `docs/plan/tichu-tournament.md` — the authoritative phased plan. When the user references a phase or the overall roadmap, read it there.
- **Tickets**: GitHub Issues on `polarode/tichu-turnier`, collected in the "Tichu Turnier" project. Each issue carries a *Blocked by* section referencing the blocking issue numbers, and the `ready-for-agent` label marks issues whose blockers are resolved. Done work is closed with the `done` label. Do not create local ticket files in the repo.
- **Status in the plan**: `docs/plan/tichu-tournament.md` phase headers note which issues delivered them; keep those references current as issues are completed.

## Conventional Commits

Use [Conventional Commits](https://www.conventionalcommits.org/) for all git commits:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types**:
- `feat`: new feature (triggers minor version bump)
- `fix`: bug fix (triggers patch version bump)
- `docs`: documentation changes only
- `refactor`: code change that neither fixes a bug nor adds a feature
- `test`: adding or updating tests only
- `chore`: maintenance tasks (build, CI, deps)
- `ci`: CI configuration changes
- `build`: build system changes

**Scope**: optional area in parentheses (e.g., `backend`, `frontend`, `plan`, `ci`)

**Examples**:
- `ci: Add GitHub Actions workflows for backend and frontend`
- `feat(backend): replace in-memory repo with Postgres + Flyway`
- `fix(frontend): resolve team token leak in magic link flow`
- `refactor(backend): extract Swiss pairing logic to domain layer`

Body/footer format:
- Use **imperative mood** in body: "add" not "added", "fix" not "fixed"
- Reference issues with `#<number>` (e.g., `refs #23`)

Commit messages should be **descriptive**, especially for the first commit of a PR.

## Backend (`backend/`) — Kotlin + Spring Boot

Hexagonal (ports & adapters). Dependencies point inward: `infra` → `application`
→ `domain`. `domain` depends on nothing framework-specific.

```
backend/src/main/kotlin/com/example/template/
├── domain/              # entities + port interfaces (TaskRepository). No Spring.
├── application/
│   ├── api/             # inbound ports: use-case interfaces (CreateTask, ListTasks, ...)
│   └── usecase/         # use-case implementations + UseCaseConfiguration (@Bean wiring)
├── infra/
│   ├── rest/            # controllers + RestConfiguration (@Bean) + error handling
│   └── persistence/     # repository adapter (in-memory) + PersistenceConfiguration (@Bean)
├── vocabulary/          # shared exceptions / value types
└── main/                # @SpringBootApplication + @Import of every configuration
```

Rules:
- **All wiring via `@Configuration` + `@Bean`.** Do **not** annotate business
  classes with `@Service`/`@Repository`/`@Component`, and do not use
  `@Autowired`. Use cases and adapters are plain classes; a `*Configuration`
  class constructs them as beans.
- A use case implements an interface from `application/api`. Controllers depend on
  those interfaces, never on the implementations.
- The `domain` layer defines repository **interfaces** (ports); `infra/persistence`
  provides the implementation (adapter).
- `main/Application.kt` wires everything together with `@Import`.

Run checks:
```bash
cd backend
./gradlew test        # unit + integration tests
./gradlew build       # compile + test + assemble
```

## Frontend (`frontend/`) — React + Vite + TanStack (pnpm workspace)

```
frontend/
├── apps/web/src/
│   ├── task-api/        # one folder per upstream API:
│   │   ├── task.api.ts        # axios instance (baseURL, interceptors)
│   │   ├── task.repository.ts # functions that call the API and map DTO→domain
│   │   ├── dtos/              # zod schemas describing the wire format
│   │   └── mappers/          # pure DTO↔domain mapping functions
│   ├── domain/          # plain domain models (framework-free)
│   ├── vocabulary/      # enums / shared value types
│   ├── features/        # feature folders: components + hooks + queries
│   ├── shared/          # cross-cutting: stores (zustand), query config, utils, components
│   ├── routes/          # TanStack Router file-based routes (routeTree.gen.ts is generated)
│   ├── router.ts        # QueryClient + router creation
│   └── main.tsx         # app entry
└── packages/ui/         # reusable component library (Button, Input, ...) consumed by apps
```

Conventions:
- **Data flow:** component → TanStack Query hook → repository → api client. The
  repository is the only place that knows about DTOs; everything above it uses
  domain types. Mappers convert DTO↔domain and are unit-tested in isolation.
- **Validation:** zod schemas live next to the DTOs and parse every response, so
  the wire format is validated at the boundary.
- **Server state** lives in TanStack Query (keyed by `shared/constants/query-keys.ts`).
  **Client/UI state** lives in Zustand stores under `shared/stores`.
- **Forms** use react-hook-form + `@hookform/resolvers/zod`.
- **Styling** is Tailwind CSS; shared primitives come from `@template/ui`.
- File naming: `*.component.tsx`, `*.hook.ts`, `*.repository.ts`, `*.mapper.ts`,
  `*.dto.ts`, `*.domain.ts`, `*.store.ts`, tests `*.spec.ts(x)` under `__tests__`.

Run checks:
```bash
cd frontend
pnpm install
pnpm typecheck
pnpm lint
pnpm test
```
