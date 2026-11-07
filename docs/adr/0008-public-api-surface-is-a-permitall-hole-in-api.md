# 0008 — The public API surface is an explicit permitAll hole inside /api/**

Date: 2026-11-07 · Status: accepted · Context: issue #125 (S9a), parent #37 (S9)

## Decision

`/api/**` is default-closed: an admin session for most routes, a team session for
`/api/team/**`. Public read endpoints are a deliberate relaxation of that posture.
They live under the prefix **`/api/public/**`** and are opened by a single
`requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()` rule placed **ahead of
the `/api/**` admin catch-all** in `SecurityConfiguration`.

Public endpoints expose only public facts (id, name, status, team count) and never team
tokens, session or credential material, or access-link state. A tournament that is not
public **does not exist** on a public endpoint: the answer is **not found**, never
*forbidden*.

## Why

- **Default-closed stays the default.** Keeping the relaxation to one GET-only rule under
  a dedicated prefix, above the catch-all, means every other `/api/**` route is still
  closed by the rule that follows. The hole is small, named, and visible in one place.
- **Not-found over forbidden.** *Forbidden* would confirm a private tournament exists and
  leak its id-space to anonymous probing. *Not found* reveals nothing: a non-public
  tournament and a non-existent one look identical to a spectator.
- **GET-only.** Spectators only read. Restricting the permitAll to GET keeps writes closed
  even if a public controller later grows a non-GET mapping by mistake.

## Considered options

- *A separate SecurityFilterChain for public routes.* Rejected for S9a: one more chain to
  reason about for a single GET prefix, when an ordered matcher in the existing chain is
  enough and keeps the whole order readable top-to-bottom.
- *Forbidden (403) for non-public tournaments.* Rejected: leaks existence (see above).

## Consequences

- Both session filters still run on public requests, so an admin cookie sets
  `ROLE_ADMIN_SESSION` even on `/api/public/**`. Public controllers must therefore derive
  nothing from the principal — visibility is decided solely by the `public_visibility`
  flag and status.
- New public endpoints must sit under `/api/public/**` to inherit the rule; anything
  outside that prefix stays closed by the admin catch-all.
- The public stream (S9d) and the public tournament page (S9b/S9c) extend this same
  surface and contract rather than introducing their own.
