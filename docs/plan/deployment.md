# Plan: Deployment (v2)

> Companion to [`tichu-tournament.md`](./tichu-tournament.md). Phase 17 of that plan
> requires "one-command deploy documented; CI builds, tests, and deploys; production
> URL reachable". This plan delivers exactly that, in phases.
>
> **Chosen architecture: single origin.** One Spring Boot container serves both the
> JSON API and the built React app; a managed free Postgres holds the data.

## Decision log

Locked decisions (change deliberately, not incidentally):

- **Frontend serving**: baked into the backend image and served by Spring Boot. One
  origin ⇒ no CORS, no `SameSite` cross-site problems, SSE "just works".
- **Backend host**: **Render**, free web service (Docker runtime).
- **Database**: **Neon** free Postgres (primary). **Supabase** free Postgres is the
  accepted fallback if Neon causes trouble — the app only needs a Postgres JDBC URL.
- **Cold-start policy**: ship on the free tier first. If the 15-minute spin-down proves
  annoying in practice, either add a keep-warm ping (Phase D7) or pay for Render Pro.
  Free-tier limits are re-verified at decision time, since they change.
- **Custom domain**: not required for v1; optional later (Phase D8).
- **Supabase scope**: used as managed Postgres only. Its auth/storage/realtime layers
  are unused — v2 has its own backend auth.

### Why not the alternatives

- **GitHub Pages + separate backend**: GitHub Pages cannot proxy `/api`, so the API and
  UI land on different registrable domains. The `SameSite=Lax` session cookie
  (`SessionCookieFactory.kt`) is then not sent on cross-site requests, and login breaks.
  Only viable with a custom domain on both sides (e.g. `app.example.com` +
  `api.example.com`) — deferred to Phase D8.
- **Cloud Run**: better cold starts, but needs a billing account, its ~1 GiB free egress
  and per-request-duration billing make open SSE connections expensive, and custom
  domains need a load balancer. See "Alternatives" at the end.

## Phase overview

- [ ] **Phase D1** — Single-origin build (backend serves the SPA)
- [ ] **Phase D2** — Production runtime configuration
- [ ] **Phase D3** — Managed Postgres (Neon)
- [ ] **Phase D4** — First deploy on Render (free)
- [ ] **Phase D5** — CI/CD: auto-deploy on `main`
- [ ] **Phase D6** — Operations: health, wake UX, backups, logs
- [ ] **Phase D7** — Cold-start mitigation (conditional)
- [ ] **Phase D8** — Custom domain (optional)

D1–D4 are the critical path: after D4 the app is live. D5–D6 make it maintainable.

---

## Phase D1 — Single-origin build (backend serves the SPA)

**Goal**: one container image that serves the built React app and the API from the
same origin, so cookies and SSE are same-origin by construction.

### What to build

A repo-root production `Dockerfile` with three stages: build the frontend with pnpm,
build the boot jar with Gradle (with the frontend `dist/` on the classpath), and a slim
JRE runtime. Plus the two backend changes the SPA needs: a fallback that serves
`index.html` for client routes, and a security matcher that stops protecting static
assets.

### Steps

- [ ] Add root `Dockerfile` (production). Keep `backend/Dockerfile` as the API-only
      image for local/dev use, and document the difference.
  ```dockerfile
  # 1) frontend
  FROM node:22-alpine AS frontend
  WORKDIR /repo/frontend
  RUN corepack enable
  COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
  COPY frontend/apps ./apps
  COPY frontend/packages ./packages
  COPY frontend/tsconfig.base.json frontend/eslint.config.mjs ./
  RUN pnpm install --frozen-lockfile
  ARG VITE_API_URL=same-origin
  ENV VITE_API_URL=$VITE_API_URL
  RUN pnpm --filter @tichu/web build

  # 2) backend (boot jar, SPA on the classpath)
  FROM gradle:9-jdk21 AS backend
  WORKDIR /app
  COPY backend/settings.gradle.kts backend/build.gradle.kts ./
  COPY backend/src ./src
  COPY --from=frontend /repo/frontend/apps/web/dist ./src/main/resources/static
  RUN gradle bootJar --no-daemon

  # 3) runtime
  FROM eclipse-temurin:21-jre-alpine
  WORKDIR /app
  COPY --from=backend /app/build/libs/app.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- [ ] Teach the API client a host-agnostic base so the image is not tied to one URL
      (`apps/web/src/shared/api/api-client.ts`):
  ```ts
  const configured = import.meta.env.VITE_API_URL;
  const base =
    configured === 'same-origin' ? globalThis.location.origin
    : configured ?? 'http://localhost:8080';
  return new URL(path, base).toString();
  ```
  (`same-origin` is the production value; local dev keeps the `localhost:8080` default.)
- [ ] Add the SPA fallback in `infra/rest` (`SpaConfiguration`, `WebMvcConfigurer`):
      serve `classpath:/static/`, resolve misses to `index.html`, but **never** fall
      back for `api/**` so unknown API paths still return a JSON 404.
- [ ] Fix the security matcher in `SecurityConfiguration.kt` so only the API requires a
      session:
  ```kotlin
  authorize.requestMatchers("/api/**").authenticated()
  authorize.anyRequest().permitAll()
  ```
  Keep the existing `POST /api/auth/register|login` permits. (`anyRequest().permitAll()`
  is safe because every app endpoint lives under `/api/**`; Phase 17 public read
  endpoints are a later, deliberate relaxation inside `/api/**`.)
- [ ] Verify locally: build the image, run it against a local Postgres, open the SPA,
      deep-link to `/admin`, and confirm a bad API path returns JSON 404, not HTML.

### Acceptance criteria

- [ ] `docker build -t tichu .` succeeds from a clean checkout (no host Node/Gradle).
- [ ] The running image serves `/`, `/admin/...` (deep link) and `/api/**` on one origin.
- [ ] `GET /api/does-not-exist` returns a JSON `ProblemDetail`, not `index.html`.
- [ ] An unauthenticated `GET /api/tournaments` returns `401`; static assets return `200`.
- [ ] A test covers the SPA/API fallback boundary.

---

## Phase D2 — Production runtime configuration

**Goal**: the app boots correctly behind a TLS-terminating proxy and exposes the
settings a host needs, without code changes per environment.

### Steps

- [ ] `application.yml`: `server.port: ${PORT:8080}` and
      `server.forward-headers-strategy: framework` (correct scheme/host behind a proxy).
- [ ] Add `application-prod.yml`:
  ```yaml
  spring:
    datasource:
      url: ${SPRING_DATASOURCE_URL}
      username: ${SPRING_DATASOURCE_USERNAME}
      password: ${SPRING_DATASOURCE_PASSWORD}
      hikari:
        maximum-pool-size: 5
    flyway:
      # Migrations run over the direct connection; the app pool may use a pooler.
      url: ${SPRING_FLYWAY_URL:${SPRING_DATASOURCE_URL}}
      user: ${SPRING_DATASOURCE_USERNAME}
      password: ${SPRING_DATASOURCE_PASSWORD}
  app:
    frontend-url: ${APP_FRONTEND_URL}
  ```
- [ ] Confirm production keeps `app.auth.session-cookie-secure: true` (default) — HTTPS
      is where `Secure` matters.
- [ ] Add a public `GET /health` endpoint (`HealthController`, no session) for host
      health checks and a future keep-warm ping. Keep it out of `/api/**`.
- [ ] Document every env var in `docs/deployment.md` / README:
      `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
      `SPRING_FLYWAY_URL`, `APP_FRONTEND_URL`, `OWNER_EMAIL`, `OWNER_PASSWORD_HASH`,
      `SPRING_PROFILES_ACTIVE`, `JAVA_TOOL_OPTIONS`, `PORT`.

### Acceptance criteria

- [ ] `SPRING_PROFILES_ACTIVE=prod` boots from env vars alone (no file edits).
- [ ] `GET /health` answers `200` without a session; the security test asserts it is public.
- [ ] No secret is baked into the image or committed; `.env` stays git-ignored.

---

## Phase D3 — Managed Postgres (Neon)

**Goal**: a durable free Postgres the app migrates on boot.

### Steps

- [ ] Create a Neon project (region close to the app). Note the **direct** and
      **pooled** connection strings.
- [ ] Create the application role/database if Neon's default is not what you want.
- [ ] Record the JDBC URLs:
      `jdbc:postgresql://<host>/<db>?sslmode=require` (direct, for Flyway) and the
      `-pooler` host for the runtime pool.
- [ ] First run applies the Flyway migrations on an empty database; verify the schema.
- [ ] If the pooled (transaction-mode) endpoint is used at runtime, append
      `prepareThreshold=0` to the JDBC URL so pgjdbc prepared statements survive pooling.
- [ ] Note the Supabase fallback: same steps, direct `:5432` for Flyway, Supavisor
      pooler for runtime; Supabase pauses after ~7 idle days, so expect a slow first
      query after a quiet week.

### Acceptance criteria

- [ ] Flyway applies `V1…Vn` cleanly on the empty Neon database and `ddl-auto: validate`
      passes on the next boot.
- [ ] Data survives a redeploy (create a tournament, redeploy, it is still there).
- [ ] Connection works with `sslmode=require`; no plaintext connection.

---

## Phase D4 — First deploy on Render (free)

**Goal**: the app is reachable at a public HTTPS URL and an owner can log in.

### Steps

- [ ] Create a Render **Web Service** (Docker runtime), production `Dockerfile` at the
      repo root, free instance type.
- [ ] Set env vars: `SPRING_PROFILES_ACTIVE=prod`,
      `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` (Neon pooled),
      `SPRING_FLYWAY_URL` (Neon direct), `APP_FRONTEND_URL=https://<service>.onrender.com`,
      `OWNER_EMAIL`, `OWNER_PASSWORD_HASH` (bcrypt), `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.
      Generate the hash with
      `htpasswd -bnBC 10 "" '<password>' | sed 's/^://' | tr -d '\n'`.
- [ ] Set the health check path to `/health`.
- [ ] Deploy, watch the logs for a clean Flyway run and Tomcat start.
- [ ] Log in as the bootstrapped owner; create a tournament; confirm it persists across
      a manual restart.
- [ ] Confirm the cold start is endurable: let it idle > 15 min, hit the URL, note the
      wake time and whether the SPA surfaces a sane error/retry on the first failed call.

### Acceptance criteria

- [ ] Public URL serves the SPA and API; login works over HTTPS.
- [ ] Flyway migrations applied; a restart keeps data.
- [ ] `APP_FRONTEND_URL` matches the real origin (CORS config is correct even though
      same-origin makes it a no-op).
- [ ] Owner bootstrap happened exactly once and no open owner-creation path exists.

---

## Phase D5 — CI/CD: auto-deploy on `main`

**Goal**: merges to `main` deploy automatically, gated by the existing test suites.

### Steps

- [ ] Enable Render's GitHub integration with auto-deploy on `main`, and require the
      existing `CI - Backend` / `CI - Frontend` checks to pass before deploy if the plan
     / Render supports it.
- [ ] If deploy must be explicit, add `.github/workflows/deploy.yml` that runs after the
      test jobs and `POST`s a Render **deploy hook** (stored as a repo secret). Never
      commit the hook URL.
- [ ] Add the production branch/paths filter so backend-only or frontend-only changes
      still trigger a rebuild of the single image (the image contains both).
- [ ] Document the release flow: merge to `main` → CI → image build → Render deploy.

### Acceptance criteria

- [ ] A green push to `main` results in a new deploy with no manual step.
- [ ] A failing test suite blocks the deploy.
- [ ] The deploy hook URL exists only as a secret, never in git history.

---

## Phase D6 — Operations: health, wake UX, backups, logs

**Goal**: the deployment is operable with free tools and behaves gracefully when cold.

### Steps

- [ ] Frontend: handle the first cold request gracefully — a localized "waking up…"
      state and a retry on network/5xx (TanStack Query `retry` + a small delay), so a
      30–90 s Render cold start does not look like a broken app.
- [ ] Confirm request errors are logged and visible in Render's log stream; document how
      to tail them.
- [ ] Document Neon backups (branching / restore) and, if desired, add a scheduled
      `pg_dump` export as a belt-and-braces backup.
- [ ] Document how to rotate the owner password hash and database credentials.
- [ ] Note the Render free instance-hours budget (~750/month; one 24/7 service is
      ~730 h) so keep-warm decisions are informed.

### Acceptance criteria

- [ ] A cold start shows "waking up…" and then succeeds without a manual refresh.
- [ ] Logs from a failed request are findable via the documented path.
- [ ] The README documents backup/restore and credential rotation.

---

## Phase D7 — Cold-start mitigation (conditional)

**Goal**: remove the 15-minute spin-down *if* D4/D6 show it is worth it. Do not build
this pre-emptively.

### Steps (pick one after observing real usage)

- [ ] **Keep-warm ping**: a scheduled GitHub Actions job (or external uptime monitor)
      hits `/health` every ~10 min. Document the trade-off: it consumes instance hours
      and Render discourages artificial keep-alives, so prefer pinning it to expected
      tournament windows over 24/7.
- [ ] **Pay for Render Pro** (~$7/mo): removes spin-down and the instance-hours cap.
      The pragmatic choice if tournaments are frequent.
- [ ] **Re-platform**: if neither fits, revisit Cloud Run / Oracle Always Free — the
      architecture is host-agnostic (one image + a JDBC URL).

### Acceptance criteria

- [ ] The chosen mitigation is documented with its cost/limit implications.
- [ ] If a ping is used, it targets `/health`, is off by default, and is easy to disable.

---

## Phase D8 — Custom domain (optional)

**Goal**: a stable, memorable URL, and (if wanted) the same-site split-hosting option.

### Steps

- [ ] Add the domain in Render; TLS is automatic.
- [ ] Update `APP_FRONTEND_URL` to the domain and redeploy.
- [ ] Rebuild the frontend if switching away from `same-origin` to an absolute API URL.
- [ ] Document the GitHub Pages + `app.`/`api.` subdomain variant here if it is ever
      chosen — it is the only split-hosting arrangement that keeps `SameSite=Lax`
      cookies working.

### Acceptance criteria

- [ ] The custom domain serves the app over HTTPS and login still works.
- [ ] `APP_FRONTEND_URL` and the build-time API base agree.

---

## Explicitly out of scope (deferred)

- Multi-region, autoscaling, or zero-cold-start guarantees.
- CI-driven database migrations on a separate runner (migrations run on app boot).
- Custom observability (metrics/tracing) beyond host logs and `/health`.
- Load/performance testing.

## Alternatives (reference only)

| Option | When it wins | Why not now |
| --- | --- | --- |
| GitHub Pages + same-site subdomains | You want to keep Pages and own a domain | Needs a domain; Pages cannot proxy `/api` |
| Netlify / Vercel / Cloudflare Pages proxy | Static-host workflow, no domain | Another vendor; proxy config to maintain |
| Cloud Run | Fast cold starts, GCP | Card required; ~1 GiB free egress; SSE billed for connection lifetime; custom domain needs a load balancer |
| Oracle Always Free VM | Always-on, no cold start | You own OS/Docker/TLS/backups |
| Render Pro | No spin-down, minimal change | Costs ~$7/mo |
