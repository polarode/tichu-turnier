# Self-hosted VPS deployment (Coolify, GHCR, on-VPS Postgres)

Production runs on a single operator-owned VPS (1 vCPU, 2 GB RAM, 60 GB disk)
orchestrated by Coolify, pulling the CI-built image from the public GHCR
package `ghcr.io/polarode/tichu-turnier` and running a Coolify-managed
PostgreSQL 17 container on the same box. TLS is terminated by Coolify's
built-in proxy (Traefik) with a free, auto-renewed Let's Encrypt certificate
(#49, #50). This replaces the first deploy on Render's free tier with a Neon
Postgres (#45), which proved unusable in practice: 200–500 ms responses and
~4-minute cold starts after idle spin-down.

Why: the app is a small tournament tool with modest traffic, so renting
reliability from a managed PaaS costs more than operating one small VM. The
free tiers that made the first deploy free are too slow to be usable in
production, and the first paid PaaS tier is far more expensive than a VPS.
Owning the box keeps the deployment trivially understandable — one VM, one
Docker image, one Postgres container — and removes the provider-specific
workarounds the Neon setup required (transaction pooler for the runtime,
direct endpoint for Flyway, `prepareThreshold=0`).

This decision does **not** supersede ADR 0002 (single origin, single image):
that decision still holds and is unchanged. Only the deployment context around
it changed. The sentence in ADR 0002 naming Render + Neon is historical
context from when it was written, not part of its decision.

## Consequences

- Operating the host is a human responsibility: swapfile, Docker, Coolify
  upgrades, provider firewall (22, 80, 443), and DNS are maintained by hand.
  `scripts/provision-vps.sh` walks through provisioning; there is no
  infrastructure-as-code for the host itself.
- The database lives on the same VM. A VM loss means data loss unless backups
  are taken; there is no provider-managed database to fall back on. There is
  no pooler in front of Postgres, so `SPRING_FLYWAY_URL` and
  `SPRING_DATASOURCE_URL` are the same URL — the fail-fast requirement (#44)
  stays.
- Auto-deploy on push to `main` is preserved by different mechanics: CI builds
  and pushes `sha-<git-sha>` and `latest` tags to a public GHCR package, and
  Coolify redeploys when `latest` changes. The image must stay public for the
  VPS to pull it without registry credentials (#43: no secrets baked in).
- TLS termination and HTTP→HTTPS redirect are delegated to Coolify's proxy,
  not the app. The app must trust `X-Forwarded-*` (forward-headers-strategy)
  so cookies and redirects see the real scheme; prod cookies are `Secure` by
  default with no override in production.
- Postgres runs on one shared 2 GB box with the app; a 2 GB swapfile is the
  memory safety net, and the JVM is capped with
  `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. If the app outgrows the box,
  this decision must be revisited deliberately.
- The single-instance assumption in the rate limiter (`ExchangeRateLimiter`)
  still holds: one container, one JVM. Scaling out would also invalidate this
  decision.
