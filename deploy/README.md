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
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production — the prod profile turns on Flyway, `ddl-auto: validate` and the fail-fast `SPRING_FLYWAY_URL` check. Unset, the app silently runs the `default` profile (as caught during the #49 deploy). |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres connection (required). |
| `SPRING_FLYWAY_URL` / `_USERNAME` / `_PASSWORD` | Flyway's own connection (required in the `prod` profile, no default — on Neon it must point at a direct, non-pooled endpoint). With the on-VPS Postgres there is no pooler, so both URLs are the same; see the topology section below. Credentials default to the datasource's and are usually omitted. |
| `APP_FRONTEND_URL` | Public origin of the app. Base for absolute invite links and the CORS allowed origin — with a single origin it must be the URL users browse to (e.g. `http://localhost:8080`). |
| `OWNER_EMAIL` / `OWNER_PASSWORD_HASH` | First platform owner, provisioned idempotently at startup. `OWNER_PASSWORD_HASH` is a bcrypt hash, never plaintext. Leave both unset to skip provisioning. |
| `APP_AUTH_SESSION_COOKIE_SECURE` | Cookies are `Secure` by default. On plain HTTP against a non-localhost host, set this to `false` (localhost is always a secure context in browsers). |

## Health check

`GET /health` answers unauthenticated with `{"status":"UP"}`; it includes a
database check, so an unreachable database yields 503 (readiness semantics).

## Production (own VPS: Coolify + GHCR + on-VPS Postgres)

Production runs the same single-origin image on the operator's own VPS
(1 vCPU, 2 GB RAM, 60 GB disk), orchestrated by Coolify (issue #49).

Topology:

- **Image**: CI (`.github/workflows/deploy-image.yml`) builds `deploy/Dockerfile`
  on every push to `main` and pushes it to the **public** GHCR package
  `ghcr.io/polarode/tichu-turnier`, tagged `sha-<git-sha>` (immutable) and
  `latest`. No secrets are baked in (#43); PRs build the image without pushing.
- **Registry → VPS**: the image is public, so the VPS pulls it without any
  registry credentials. Coolify's GHCR integration redeploys when `latest`
  changes, preserving auto-deploy-on-`main` like Render did.
- **Database**: Postgres runs on the VPS as a Coolify-managed container with a
  persistent Docker volume. It replaced the Neon project (DB was empty, so the
  cutover was zero-risk; the Neon project is deleted once the cutover is
  verified).
- **Flyway note**: with the on-VPS Postgres there is no pooler, so
  `SPRING_FLYWAY_URL` and `SPRING_DATASOURCE_URL` point at the *same*
  host:port/db. The strict fail-fast requirement for `SPRING_FLYWAY_URL` in the
  `prod` profile stays (#44) — set both, always.
- **TLS**: the app is reachable over plain HTTP via the provider-assigned
  hostname for now; the session cookie runs with
  `APP_AUTH_SESSION_COOKIE_SECURE=false` until S10.7 adds TLS.
- **Memory**: the box has a 2 GB swapfile as insurance; the JVM gets
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.

Provisioning is a human-only procedure (SSH, dashboard clicks); the
`scripts/provision-vps.sh` wizard walks through it step by step and captures
the values.

## Previous provider: Neon + Render (deprecated)

The first deploy ran on Render's free tier with a Neon Postgres, defined by the
[`render.yaml`](../render.yaml) blueprint and provisioned with
`scripts/provision-production.sh`. These artifacts are kept until the S10.7
cutover retires the Render service; then `render.yaml`,
`provision-production.sh` and this section go away. Highlights, in case the
fallback is ever needed again: Neon transaction pooler for the runtime + direct
endpoint for Flyway; Supabase as fallback provider.

## Production verification checklist

Run through this after the first deploy — record the results here; a summary
goes into the closing comment of the deploy issue.

| # | Check | How | Result |
| --- | --- | --- | --- |
| 1 | First boot applies all Flyway migrations on the empty DB | Coolify deploy log shows `Migrating schema …` with no errors | |
| 2 | `ddl-auto: validate` passes | No `Schema-validation` errors in the boot log on every subsequent boot | |
| 3 | Live URL serves the app | Open the provider-assigned hostname; the SPA loads | |
| 4 | Owner login works | Log in at `/admin` (cookie `Secure` may be false over plain HTTP until S10.7) | |
| 5 | Data survives a restart | Create some data, then restart the container in Coolify; data is still there | |
| 6 | Data survives a redeploy | Push a commit to `main` (auto-deploy); data is still there | |
| 7 | CI image pipeline works | Push to `main` → new `sha-<sha>` + `latest` on GHCR → Coolify redeploys | |
| 8 | `/health` is UP | `curl http://…/health` → `{"status":"UP"}` | |
| 9 | Unknown API path stays JSON | `curl http://…/api/nonexistent` → JSON ProblemDetail 404, not HTML | |

## What the image contains

- Multi-stage build: Node + pnpm build the SPA (`VITE_API_URL=/`), Gradle builds
  the boot jar with the SPA bundle on the classpath, and the runtime is a bare
  JRE running the jar.
- No secrets are baked in: everything sensitive is runtime configuration.
- The per-app Dockerfiles (`backend/Dockerfile`, dev compose setup) are
  untouched and remain the local development path.
