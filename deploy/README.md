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

Day-to-day operation of the production deploy (logs, backups, restore drills,
credential rotation, patching) is covered by the
[operations runbook](../docs/operations.md).

> `host.docker.internal` is how the container reaches a Postgres running on the
> host (Docker Desktop; on Linux add `--add-host=host.docker.internal:host-gateway`).
> Alternatively run the image inside the compose network so it can talk to
> `postgres:5432`.

## Environment variables

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production — the prod profile turns on Flyway, `ddl-auto: validate` and the fail-fast `SPRING_FLYWAY_URL` check. Unset, the app silently runs the `default` profile. |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres connection (required). |
| `SPRING_FLYWAY_URL` / `_USERNAME` / `_PASSWORD` | Flyway's own connection (required in the `prod` profile, no default). With the on-VPS Postgres there is no pooler, so both URLs are the same; see the topology section below. Credentials default to the datasource's and are usually omitted. |
| `APP_FRONTEND_URL` | Public origin of the app. Base for absolute invite links and the CORS allowed origin — with a single origin it must be the URL users browse to (e.g. `https://tichu.example.com`). |
| `OWNER_EMAIL` / `OWNER_PASSWORD_HASH` | First platform owner, provisioned idempotently at startup. `OWNER_PASSWORD_HASH` is a bcrypt hash, never plaintext. Leave both unset to skip provisioning. |
| `APP_AUTH_SESSION_COOKIE_SECURE` | Cookies are `Secure` by default. Only set this to `false` for plain-HTTP testing against a non-localhost host; production over HTTPS needs no variable at all. |

## Health check

`GET /health` answers unauthenticated with `{"status":"UP"}`; it includes a
database check, so an unreachable database yields 503 (readiness semantics).

## Production (own VPS: Coolify + GHCR + on-VPS Postgres + HTTPS)

Production runs the single-origin image on the operator's own VPS
(1 vCPU, 2 GB RAM, 60 GB disk), orchestrated by Coolify (#49), named and
secured with a subdomain + Let's Encrypt (#50).

Topology:

- **Image**: CI (`.github/workflows/deploy-image.yml`) builds `deploy/Dockerfile`
  on every push to `main` and pushes it to the **public** GHCR package
  `ghcr.io/polarode/tichu-turnier`, tagged `sha-<git-sha>` (immutable) and
  `latest`. No secrets are baked in (#43); PRs build the image without pushing.
- **Registry → VPS**: the image is public, so the VPS pulls it without any
  registry credentials. Coolify's GHCR integration redeploys when `latest`
  changes — auto-deploy-on-`main`.
- **Database**: Postgres runs on the VPS as a Coolify-managed container with a
  persistent Docker volume.
- **Flyway note**: with the on-VPS Postgres there is no pooler, so
  `SPRING_FLYWAY_URL` and `SPRING_DATASOURCE_URL` point at the *same*
  host:port/db. The strict fail-fast requirement for `SPRING_FLYWAY_URL` in the
  `prod` profile stays (#44) — set both, always.
- **DNS & TLS**: a subdomain under the operator's own domain has an A record
  pointing at the VPS IP. Coolify's built-in proxy (Traefik) terminates TLS with
  a free, auto-renewed Let's Encrypt certificate (HTTP-01 challenge — ports 80
  and 443 must be reachable); HTTP redirects to HTTPS. There is no paid SSL
  anywhere.
- **Cookies**: behind the TLS-terminating proxy, `server.forward-headers-strategy`
  makes the app see the real scheme, and prod cookies are `Secure` by default —
  no override needed in production (`APP_AUTH_SESSION_COOKIE_SECURE` exists only
  as an escape hatch for plain-HTTP testing).
- **Memory**: the box has a 2 GB swapfile as insurance; the JVM gets
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.

Provisioning is a human-only procedure (SSH, dashboard clicks); the
`scripts/provision-vps.sh` wizard walks through it step by step — including the
DNS A record, the Coolify domain + Let's Encrypt setup, and the teardown of the
old Render/Neon deploy — and captures the values.

## Production verification checklist

Run through this after a deploy — record the results here; a summary
goes into the closing comment of the deploy issue.

| # | Check | How | Result |
| --- | --- | --- | --- |
| 1 | First boot applies all Flyway migrations on the empty DB | Coolify deploy log shows `Migrating schema …` with no errors | |
| 2 | `ddl-auto: validate` passes | No `Schema-validation` errors in the boot log on every subsequent boot | |
| 3 | Subdomain resolves to the VPS and serves the app | `dig +short <subdomain> A` → VPS IP; open `https://<subdomain>` — the SPA loads | |
| 4 | HTTPS with valid Let's Encrypt certificate | Browser shows a trusted certificate; `curl -I http://<subdomain>` redirects to HTTPS | |
| 5 | `APP_FRONTEND_URL` is the HTTPS origin | Invite links are absolute HTTPS URLs; CORS accepts the HTTPS origin | |
| 6 | Owner login sets a `Secure` cookie over HTTPS | Log in at `/admin`; devtools → cookie has the `Secure` flag | |
| 7 | Data survives a restart | Create some data, then restart the container in Coolify; data is still there | |
| 8 | Data survives a redeploy | Push a commit to `main` (auto-deploy); data is still there | |
| 9 | CI image pipeline works | Push to `main` → new `sha-<sha>` + `latest` on GHCR → Coolify redeploys | |
| 10 | `/health` is UP | `curl https://<subdomain>/health` → `{"status":"UP"}` | |
| 11 | Unknown API path stays JSON | `curl https://<subdomain>/api/nonexistent` → JSON ProblemDetail 404, not HTML | |

## What the image contains

- Multi-stage build: Node + pnpm build the SPA (`VITE_API_URL=/`), Gradle builds
  the boot jar with the SPA bundle on the classpath, and the runtime is a bare
  JRE running the jar.
- No secrets are baked in: everything sensitive is runtime configuration.
- The per-app Dockerfiles (`backend/Dockerfile`, dev compose setup) are
  untouched and remain the local development path.
