# Single origin, single image

The frontend SPA and the backend API are served from one origin by one Docker
image: a multi-stage build produces both artifacts, and Spring Boot serves the
built SPA from the classpath (`index.html` fallback for client routes) alongside
the `/api/**` controllers. Production runs as a single Render web service backed
by one Neon Postgres.

Why: for a small tournament app, a separate static host (plus CORS, plus a second
deployment pipeline, plus two URLs for players) doubles the operational surface
for no benefit. One origin removes CORS entirely, makes session cookies trivially
correct, and lets every later slice be verified at one real URL after one deploy.

## Consequences

- The API keeps its `/api/**` namespace: unknown API paths must return JSON
  `ProblemDetail`, never the SPA's `index.html`; the SPA fallback only applies to
  non-`/api` paths.
- `GET /health` lives outside `/api/**` (unauthenticated) so the host can health
  check it without session concerns.
- Any change to either half rebuilds and redeploys the single image; there is no
  independent frontend release channel.
- Scaling the frontend independently of the API is not possible; if the app ever
  needs that, this decision must be revisited deliberately.
