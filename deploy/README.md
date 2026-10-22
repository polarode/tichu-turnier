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
| `APP_FRONTEND_URL` | Public origin of the app. Base for absolute invite links and the CORS allowed origin — with a single origin it must be the URL users browse to (e.g. `http://localhost:8080`). |
| `APP_AUTH_OWNER_EMAIL` / `APP_AUTH_OWNER_PASSWORD_HASH` | First platform owner, provisioned idempotently at startup. The password is a bcrypt hash, never plaintext. Leave unset to skip provisioning. |
| `APP_AUTH_SESSION_COOKIE_SECURE` | Cookies are `Secure` by default. On plain HTTP against a non-localhost host, set this to `false` (localhost is always a secure context in browsers). |

## Health check

`GET /health` answers unauthenticated with `{"status":"UP"}`; it includes a
database check, so an unreachable database yields 503 (readiness semantics).

## What the image contains

- Multi-stage build: Node + pnpm build the SPA (`VITE_API_URL=/`), Gradle builds
  the boot jar with the SPA bundle on the classpath, and the runtime is a bare
  JRE running the jar.
- No secrets are baked in: everything sensitive is runtime configuration.
- The per-app Dockerfiles (`backend/Dockerfile`, dev compose setup) are
  untouched and remain the local development path.
