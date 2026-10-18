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

- **Backend** — admin auth and account lifecycle (invite-gated onboarding, owner
  admin management), plus tournament and team setup (create, list, get, edit, delete
  with server-side validation). Rounds, scoring, confirmations and live events are next.
- **Frontend** — admin area with register/login, the pending-approval and invite
  states, the tournament dashboard and the owner console (approve/reject requests,
  disable/reactivate admins, issue invites), all in de/en. Tournament create/edit,
  team management and the team/public screens follow in later phases.

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

The easy path is the dev script, which reads the repo's `.env` (owner credentials,
`POSTGRES_PORT`), un-escapes the compose `$$` escaping, points the datasource at
`localhost:${POSTGRES_PORT}`, and starts Postgres via compose if nothing is listening:

```bash
scripts/dev-backend.sh   # http://localhost:8080
```

Manual equivalent (mind the differences from the docker setup): `.env` is only read
by compose — Spring needs the owner credentials as real environment variables with
**single** `$` in the bcrypt hash, and the datasource must be pointed at the port
Postgres is actually published on:

```bash
cd backend
export OWNER_EMAIL='owner@example.com'
export OWNER_PASSWORD_HASH='$2y$10$…'   # single $ — undo the .env $$ escaping
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/tichu_turnier'
./gradlew bootRun
./gradlew test                          # unit + integration tests (Testcontainers)
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

## Troubleshooting

### "Refusing to claim … as the platform owner" in the startup log

The owner bootstrap only converges credentials into an account that already has the
`OWNER` role (that row can only have come from an earlier bootstrap). If someone
self-registered with the configured `OWNER_EMAIL` before it was configured, that row
exists without the `OWNER` role and is deliberately never promoted or overwritten.
Fix it once, then restart — the bootstrap converges the password hash from config:

```bash
docker compose exec postgres psql -U tichu -d tichu_turnier -c \
  "update admin_users set role='OWNER' where email='<owner-email>';"
```

(Or delete the row instead, if that account was never wanted.)

### Sign-in fails although the owner is configured

Check, in order:

- The startup log shows the owner bootstrap acting on your email (convergence or
  refusal — see above).
- The hash handed to Spring contains **single** `$`: a `$$…` value is compose's
  `.env` escaping and is not a valid bcrypt hash. `scripts/dev-backend.sh` handles
  this; a manual `export` must undo the escaping itself.
- The datasource URL points at the port Postgres is actually published on
  (`POSTGRES_PORT` from `.env`, not necessarily 5432).
