# Architecture & conventions

This is a two-app monorepo. Read the relevant section before editing an area.

## Git & pushing

- **Never push to main.** Commit locally with Conventional Commits, but leave `git push` to the operator as a manual step. Do not run `git push`, `gh pr create` with immediate push, or anything else that publishes to the remote.

## Plan & tickets

- **Wayfinder map**: issue #27 on `polarode/tichu-turnier` — the authoritative plan. Work is organized as feature slices (S1–S11), each a child issue of the map with the full spec in its body. Invoke the wayfinder skill with the map to work through it.
- **Tickets**: GitHub Issues on `polarode/tichu-turnier`, collected in the "Tichu Turnier" project. Each issue carries a *Blocked by* section referencing the blocking issue numbers, and the `ready-for-agent` label marks issues whose blockers are resolved. Done work is closed with the `done` label. Do not create local ticket files in the repo.
- **Status in the plan**: the map's Decisions-so-far section records what is done; keep it current as slices close.

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
│   ├── <area>-api/      # one folder per upstream API:
│   │   ├── <area>.repository.ts # calls the API, parses zod, maps DTO→domain
│   │   ├── dtos/              # zod schemas describing the wire format
│   │   └── mappers/          # pure DTO↔domain mapping functions
│   ├── domain/          # plain domain models (framework-free)
│   ├── vocabulary/      # enums / shared value types (incl. backend error codes)
│   ├── features/        # feature folders: components + hooks + queries
│   ├── shared/
│   │   ├── api/         # shared axios client + typed ApiError (ProblemDetail mapping)
│   │   ├── constants/   # centralized query keys
│   │   ├── i18n/        # de/en catalogs + provider
│   │   ├── components/  # cross-cutting components (AppShell)
│   │   └── stores/      # zustand client-state stores
│   ├── routes/          # TanStack Router file-based routes (routeTree.gen.ts is generated)
│   ├── router.ts        # QueryClient + router creation
│   └── main.tsx         # app entry
└── packages/ui/         # reusable component library (Button, Input, ...) consumed by apps
```

Conventions:
- **Data flow:** component → TanStack Query hook → repository → `shared/api` axios
  client. The repository is the only place that knows about DTOs; everything above
  it uses domain types. Mappers convert DTO↔domain and are unit-tested in isolation.
- **Transport:** all requests go through the shared axios instance (`withCredentials`,
  so the httpOnly session cookie travels); its interceptor turns every failure into a
  typed `ApiError` carrying the backend's stable `code` (and `field` when anchored).
- **Validation:** zod schemas live next to the DTOs and parse every response, so
  the wire format is validated at the boundary.
- **Server state** lives in TanStack Query (keyed by `shared/constants/query-keys.ts`).
  **Client/UI state** lives in Zustand stores under `shared/stores`.
- **Forms** use react-hook-form + `@hookform/resolvers/zod`.
- **Styling** is Tailwind CSS; shared primitives come from `@tichu/ui`.
- **i18n:** all user-facing strings live in `shared/i18n/catalogs` (de + en);
  hardcoded JSX text is rejected by lint and catalog parity is enforced by a test.
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
