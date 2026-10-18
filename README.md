# Tichu Turnier

A web app for running Tichu tournaments. Organizers set up a tournament and its
teams, run Swiss rounds (with a trio table when the team count is odd), and teams
enter and confirm their game results. Everyone else can follow the tournament on
public standings and statistics. The UI ships in German and English.

This is a v2 rebuild of the earlier Supabase-based Tichu manager — same concept,
new architecture. The authoritative roadmap lives in
[`docs/plan/tichu-tournament.md`](./docs/plan/tichu-tournament.md).

## Layout

```
tichu-turnier/
├── backend/     # Kotlin + Spring Boot (Gradle), hexagonal architecture
├── frontend/    # React 19 + Vite + TanStack (pnpm workspace)
└── docs/plan/   # phased implementation plan
```

See [`AGENTS.md`](./AGENTS.md) for the architecture conventions, and the README in
each subproject for its details.

## Status

- **Backend** — admin auth and session lifecycle, plus tournament and team setup
  (create, list, get, edit, delete with server-side validation). Rounds, scoring,
  confirmations and live events are next.
- **Frontend** — app shell with the four areas (admin, team, public, login) and
  de/en i18n. The screens are wired up to the API in later phases.

## Quick start

### Everything at once (Docker)

```bash
docker compose up        # Postgres + backend + frontend dev server
```

- Backend on http://localhost:8080, frontend on http://localhost:3000
- PostgreSQL data persists in the `pgdata` volume; the backend applies Flyway
  migrations on startup
- Postgres is published on `localhost:${POSTGRES_PORT:-5432}`. If 5432 is
  already taken on your machine, `cp .env.example .env` and set `POSTGRES_PORT`
  (e.g. `5433`) — the app containers reach the database over the compose network
  regardless of the host port.
- After changing backend code, rebuild with `docker compose up --build` (compose
  reuses the existing image otherwise).
- The first platform admin (owner) is provisioned at backend startup from
  `OWNER_EMAIL` + `OWNER_PASSWORD_HASH` in `.env`; registration alone only creates a
  pending account that an owner must approve. Copy `.env.example` to `.env` and set a
  bcrypt hash to get in (see the file for the generation command).

### Backend (without Docker)

Needs PostgreSQL on `localhost:5432` (database `tichu_turnier`, user `tichu`,
password `tichu`) — e.g. start only the database with
`docker compose up postgres`. (If you published Postgres on a different host port
via `POSTGRES_PORT`, point the backend at it with
`SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:<port>/tichu_turnier`.) Then:

```bash
cd backend
./gradlew bootRun        # http://localhost:8080
./gradlew test           # unit + integration tests (Testcontainers)
```

The schema is owned by Flyway (`backend/src/main/resources/db/migration`); data
lives in PostgreSQL and survives restarts.

### Frontend (without Docker)

```bash
cd frontend
pnpm install
pnpm dev                 # http://localhost:3000
pnpm typecheck && pnpm lint && pnpm test
```

The frontend expects the backend on `http://localhost:8080` (configurable via
`VITE_API_URL`); CORS for `http://localhost:3000` is enabled in the backend by
default.

## Architecture

**Backend (hexagonal / ports & adapters):**

- `domain` — entities and port interfaces, framework-free.
- `application/api` — inbound ports (use-case interfaces).
- `application/usecase` — use-case implementations (commands & queries).
- `infra` — adapters (REST, persistence, security) and all Spring wiring.
- All beans wired explicitly via `@Configuration` + `@Bean` — no `@Service`,
  `@Repository`, `@Component`, or `@Autowired` on business classes.
- PostgreSQL + Flyway for persistence; RFC 7807 problem details with stable,
  machine-readable error codes for the API.

**Frontend (layered, feature-oriented):**

- Data flow: `component → query hook → repository → axios → zod parse → mapper → domain`.
- `shared/i18n` — German and English catalogs plus the language switch; no
  hardcoded UI strings.
- `features` — feature folders (components, hooks, queries).
- `packages/ui` — reusable component library consumed by the app.
- TanStack Router + Query, react-hook-form + zod, Tailwind CSS, Zustand.

## CI

GitHub Actions runs the backend (`./gradlew build`) and the frontend
(`pnpm typecheck && pnpm lint && pnpm test && pnpm build`) on every push and pull
request.
