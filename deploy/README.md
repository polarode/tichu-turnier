# Single-origin deploy image

One Docker image serves the built SPA and the backend API from a single origin
(ADR 0002). Build from the repository root:

```bash
docker build -f deploy/Dockerfile -t tichu-turnier .
```

## Run it locally

The backend needs a PostgreSQL database. Point it at any Postgres via
`SPRING_DATASOURCE_URL`, or spin one up with the repo's compose file first:

```bash
docker compose up -d postgres

docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/tichu_turnier \
  -e SPRING_DATASOURCE_USERNAME=tichu \
  -e SPRING_DATASOURCE_PASSWORD=tichu \
  -e APP_FRONTEND_URL=http://localhost:8080 \
  tichu-turnier
```

Then open <http://localhost:8080> — the SPA, the API (`/api/**`), and the health
endpoint all live on that one origin.

> `host.docker.internal` is how the container reaches a Postgres running on the
> host (Docker Desktop; on Linux add `--add-host=host.docker.internal:host-gateway`).
> Alternatively run the image inside the compose network so it can talk to
> `postgres:5432`.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres connection (required). |
| `SPRING_FLYWAY_URL` / `_USERNAME` / `_PASSWORD` | Flyway's own connection (required in the `prod` profile, no default — it must point at a direct, non-pooled endpoint on Neon). Credentials default to the datasource's and are usually omitted. |
| `APP_FRONTEND_URL` | Public origin of the app. Base for absolute invite links and the CORS allowed origin — with a single origin it must be the URL users browse to (e.g. `http://localhost:8080`). |
| `OWNER_EMAIL` / `OWNER_PASSWORD_HASH` | First platform owner, provisioned idempotently at startup. `OWNER_PASSWORD_HASH` is a bcrypt hash, never plaintext. Leave both unset to skip provisioning. |
| `APP_AUTH_SESSION_COOKIE_SECURE` | Cookies are `Secure` by default. On plain HTTP against a non-localhost host, set this to `false` (localhost is always a secure context in browsers). |

## Health check

`GET /health` answers unauthenticated with `{"status":"UP"}`; it includes a
database check, so an unreachable database yields 503 (readiness semantics).

## Production (Neon + Render, free tier)

The first production deploy runs the same single-origin image on Render's free
tier with a Neon Postgres, both in Frankfurt (eu-central-1).

- The Render service is defined by the [`render.yaml`](../render.yaml) blueprint
  at the repo root: Docker runtime against `deploy/Dockerfile` with the repo
  root as build context, free plan, Frankfurt region, health check path
  `/health`, auto-deploy on every push to `main`.
- Secret env vars (`SPRING_DATASOURCE_*`, `SPRING_FLYWAY_URL`, `OWNER_EMAIL`,
  `OWNER_PASSWORD_HASH`) are declared with `sync: false` — you enter the values
  once in the Render dashboard; nothing sensitive is committed.
  (`APP_FRONTEND_URL` is not a secret, but it's only known after the service
  exists, so it's declared with `sync: false` too.)
- The runtime pool uses Neon's *transaction pooler* endpoint
  (`…-pooler…`, `sslmode=require&prepareThreshold=0`); Flyway migrates through
  Neon's *direct* endpoint (`SPRING_FLYWAY_URL`). The prod profile fails fast
  at boot if the Flyway URL is missing.
- Free-tier caveat: Render spins the service down when idle, so the first
  request after a pause pays a cold start. Neon's free tier scales to zero too.
- Fallback provider: Supabase (direct `:5432` for Flyway, Supavisor pooler for
  the runtime; the project pauses after ~7 idle days).

Provisioning is a human-only procedure (account creation, dashboard clicks);
`scripts/provision-production.sh` walks through it step by step and captures
the values.

## Production verification checklist

Run through this after the first deploy — record the results and the measured
cold-start time here; a summary goes into the closing comment of the deploy
issue.

| # | Check | How | Result |
| --- | --- | --- | --- |
| 1 | First boot applies all Flyway migrations on the empty Neon DB | Render deploy log shows `Migrating schema …` with no errors; Neon console lists all tables | |
| 2 | `ddl-auto: validate` passes | No `Schema-validation` errors in the boot log on every subsequent boot | |
| 3 | Live HTTPS URL serves the app | Open the `https://….onrender.com` URL; the SPA loads | |
| 4 | Owner login works over HTTPS | Log in at `/admin`; the session cookie is set with `Secure` | |
| 5 | Data survives a restart | Create some data, then **Manual Deploy → Restart service** in Render; data is still there | |
| 6 | Data survives a redeploy | Push a commit (or trigger a deploy); data is still there | |
| 7 | Cold-start wake time | After ≥15 min idle, request the URL and time until the app answers (measure twice) | |
| 8 | `/health` is UP | `curl https://…/health` → `{"status":"UP"}` | |
| 9 | Unknown API path stays JSON | `curl https://…/api/nonexistent` → JSON ProblemDetail 404, not HTML | |

## What the image contains

- Multi-stage build: Node + pnpm build the SPA (`VITE_API_URL=/`), Gradle builds
  the boot jar with the SPA bundle on the classpath, and the runtime is a bare
  JRE running the jar.
- No secrets are baked in: everything sensitive is runtime configuration.
- The per-app Dockerfiles (`backend/Dockerfile`, dev compose setup) are
  untouched and remain the local development path.
