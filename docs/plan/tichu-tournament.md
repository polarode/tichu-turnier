# Plan: Tichu Tournament v2

> Source: Requirements gathered 2026-10-17/18 from analysis of v1 (`~/dev/github/backend`, `~/dev/github/frontend`) and decisions made with the user. v1 reference: Supabase-based Tichu tournament manager (German UI), rebuilt fresh — same concept, new architecture.

## Phase overview

- [x] **Phase 1** — Scaffold + persistence + CI
- [x] **Phase 2** — Admin auth & ownership
- [x] **Phase 3** — Tournament & team setup
- [x] **Phase 4** — Invite-gated admin onboarding
- [ ] **Phase 5** — Team magic-link access
- [ ] **Phase 6** — Start tournament: rounds & Swiss pairing
- [ ] **Phase 7** — Trio tables (odd team counts)
- [ ] **Phase 8** — Game scoring
- [ ] **Phase 9** — Confirmation & completion
- [ ] **Phase 10** — Standings, next round & finish
- [ ] **Phase 11** — SSE live events + statistics
- [x] **Phase 12** — Frontend foundation
- [ ] **Phase 13** — Admin: auth, dashboard, tournament setup
- [ ] **Phase 14** — Team: login & match overview
- [ ] **Phase 15** — Game entry (scoring form)
- [ ] **Phase 16** — Tournament management live
- [ ] **Phase 17** — Public views, statistics & ship

---

## Architectural decisions

Durable decisions that apply across all phases:

- **Repo layout**: single monorepo (`backend/` + `frontend/`), started from `~/dev/fullstack-template`, keeping its conventions as canonical (documented in the template's `AGENTS.md`).
- **Backend stack**: Kotlin 2.1 / JDK 21 / Spring Boot 3.4 / Gradle, hexagonal architecture (domain → application → infra), explicit `@Bean` wiring only (no `@Service`/`@ComponentScan`/`@Autowired` in production code), RFC 7807 `ProblemDetail` error handling, vocabulary exceptions thrown by use cases.
- **Persistence**: PostgreSQL + Flyway migrations; Spring Data JPA as the first real adapter behind the template's repository ports (the swap point the template README documents); Testcontainers for integration tests; explicit transactions on every multi-write use case.
- **API**: REST under `/api/...`; wire DTOs owned by the infra layer; frontend mirrors the contract with zod schemas parsed at the axios boundary (template convention — no OpenAPI codegen). Errors are RFC 7807 `ProblemDetail` with a stable machine-readable error code and English human-readable text; the server speaks English only — localization to de/en happens in the frontend via the error code.
- **Auth**: admins = email/password accounts (Spring Security), **invite-gated** — self-registration is a request an existing platform owner approves, and owners can also issue single-use invites. Admin accounts carry a `status` (`PENDING`/`ACTIVE`/`DISABLED`) and a `role` (`OWNER`/`ADMIN`); only owners manage admins. Teams = hardened magic link — long random cryptographically-secure token, never readable by third parties, validated only server-side, revocable by the organizer, rate-limited. Both admin and team sessions are `httpOnly` cookies (`Secure`, `SameSite=Lax`), exchanged once after magic-link/password validation; tokens never appear in URLs, headers-as-storage, or localStorage. Cookie + restricted CORS + JSON-only API avoids CSRF without extra tokens.
- **Authorization model**: every mutation ownership-checked against the authenticated principal; owner-only operations checked by role; service-role-style bypass does not exist; all rules enforced server-side regardless of client behavior. Ownership failures return 404 (resource invisible), permission/role failures return 403.
- **Schema shape**: `tournaments` → `rounds` → `tables` → `games` → `game_participants`, plus `users` (admins), `teams`, `players` (tournament-scoped, no global player table). **A table seats 2 or 3 teams** (3 = trio table); games reference the two playing teams; confirmations are per (table, team) — 2 rows for normal tables, 3 for trio tables. Tournament status machine: `setup → active → completed`, final — no reopen in v2.
- **Public visibility**: a game result becomes publicly visible (team views, public views, SSE, live standings) the moment it is entered as finished — not only after table confirmation. Results may still be edited until the table confirms, so live standings can shift until then; that is accepted behavior.
- **Scoring rules (server-authoritative)**: base scores sum to 100 (or 0 for double win); small Tichu ±100, grand Tichu ±200, double win +200; victory points per game = win 2 / tie 1 / +1 double-win bonus; position/tichu/bomb validation as in v1; `settings.allow_grand_tichu` enforced server-side; `beschiss` flag persisted.
- **Pairing**: Swiss system on victory points, tie-break total points; odd team counts produce exactly one trio table (see Phase 7); rematches allowed; organizer-facing round length is `total_rounds` chosen explicitly (no estimate formula). **Trio selection**: round 1 = random; from round 2 on, the **bottom three teams in current standings** form the trio table. This may put the same teams at the trio table repeatedly — accepted for v2, smarter selection is a later refinement.
- **Ties in standings**: if VP *and* total points are identical, teams share the same rank — no further tie-break, shared ranks are accepted output.
- **Realtime**: Server-Sent Events from the backend; clients refresh state via TanStack Query on events.
- **i18n**: German + English from day one, no hardcoded UI strings. The server speaks English only and returns stable error codes; all localization is frontend-side.

### Terminology (user-facing hierarchy)

**Tournament** (the complete event) → **round** (one of multiple rounds in the tournament) → **game** (one of the games played by two teams during a round). Tables are the organizational construct inside a round (seating 2 or 3 teams); a round is completed when all its tables are confirmed.
- **Frontend stack**: per template — React 19 / Vite / TanStack Router (file-based) + Query / Tailwind v4 / Zustand / react-hook-form + zod / axios with response interceptor for auth, strict layering `component → query hook → repository → axios → zod parse → mapper → domain`; shared primitives in `packages/ui`; Vitest + Testing Library; pnpm workspace.
- **Delivery**: Docker image for the backend, docker-compose for local dev (Postgres + backend + frontend), GitHub Actions CI (build, lint, test), deployed to a free-tier combo (container platform + managed free Postgres). The concrete, phased hosting plan is [`deployment.md`](./deployment.md): a single origin (Spring Boot serves the built SPA) on Render + Neon.

### Key domain vocabulary (shared across phases)

- **Tournament** — has rounds; each **round** has **tables**; each table seats 2 teams (normal) or 3 teams (trio); each table plays **games** (4 per normal table per round; 6 per trio table per round).
- **Trio table** — per-round construct for odd team counts: 6 games in sequence so every pair of the 3 teams meets exactly twice; each team plays 4 games and sits out 2; 3-way confirmation.
- **Victory points (VP)** — per-game (2/1/0, +1 double-win bonus); standings sorted by sum of VP, tie-broken by total points.

---

## Phase 1: Scaffold + persistence + CI ✅ (done — ticket 001)

**User stories**: As a developer I can clone one repo and run the whole stack locally with a real database, and CI proves every change is green.

### What to build

Create the new monorepo from the template, rebrand from the template's Task example to the project identity (keeping Task temporarily as the guinea pig domain), and prove the template's documented persistence swap end-to-end: Postgres via Flyway + JPA adapter behind the repository port, Testcontainers-backed integration tests, docker-compose for local dev, GitHub Actions workflow running build + tests on every push.

### Acceptance criteria

- [x] Fresh clone: `docker compose up` yields running backend on Postgres and frontend dev server, documented in README
- [x] Flyway migration applies cleanly on empty database; Task data survives restart (real persistence, no in-memory fallback in the default profile)
- [x] At least one repository integration test runs against Testcontainers Postgres in CI
- [x] GitHub Actions workflow runs build + full test suite and is green
- [x] Template conventions (AGENTS.md) carried over unchanged; no `@Service`/`@Autowired` in production code

---

## Phase 2: Admin auth & ownership ✅ (done — tickets 002–004)

**User stories**: As a tournament organizer I register and log in; I only ever see and change my own tournaments; nobody can touch my data with someone else's session or no session at all.

### What to build

Introduce admin accounts (email/password) with Spring Security token sessions, wire the authenticated principal through the use cases, and establish the ownership rule: every tournament-scoped read/write verifies the caller owns the resource. Error responses stay RFC 7807 (401/403 distinguished). This phase fixes v1's worst class of bugs before any feature depends on it.

### Acceptance criteria

- [x] Register, login, logout, session refresh endpoints work and are documented
- [x] Unauthenticated requests to protected endpoints get 401; foreign-resource access gets 403/404, never data
- [x] Ownership check is exercised by tests (user A cannot read/modify user B's tournament)
- [x] Password handling follows current security practice (no plaintext, proper hashing)
- [x] CORS restricted to the configured frontend origin

---

## Phase 3: Tournament & team setup ✅ (done — tickets 005–008)

**User stories**: As an organizer I create a tournament, describe it, set limits and settings (incl. grand Tichu toggle); I add teams with two named players each, catching duplicates and rule violations with clear, field-anchored error messages (stable server error codes, localized in the frontend).

### What to build

Full setup-phase CRUD: tournaments (name, description, max teams, settings, status `setup`), teams and their two players — players scoped to the tournament (no global player table), server-side validation ported from v1: unique team name per tournament, no player on two teams in one tournament, distinct players per team, no more than max teams. All mutations ownership-checked and transactional.

### Acceptance criteria

- [x] Create/edit/delete tournament and teams via API, all returning typed validation errors
- [x] Duplicate player / duplicate team name / over-capacity violations rejected server-side with tests for each
- [x] Players are tournament-scoped: identical names allowed in different tournaments, impossible within one
- [x] Use-case unit tests cover the rule matrix; integration tests cover the endpoints

---

## Phase 4: Invite-gated admin onboarding ✅ (done — tickets #21, #22, #23)

**User stories**: As the platform owner I decide who becomes a tournament admin — I approve or reject registration requests and can invite someone directly; as a prospective organizer I register, wait to be approved (or use my invite), and only then can log in.

### What to build

Close open registration. Admin accounts gain a lifecycle — `status` (`PENDING`, `ACTIVE`, `DISABLED`) and `role` (`OWNER`, `ADMIN`) — and only owners manage admins:

- **Owner bootstrap**: the first owner is provisioned idempotently from configuration (email + password hash from env), with no open endpoint that can mint an owner. Where a system already has admins, existing rows migrate to `ACTIVE`/`ADMIN`.
- **Registration as a request**: `POST /api/auth/register` creates a `PENDING` account (password still hashed). A pending or disabled account cannot log in: after *correct* credentials it gets a stable `ACCOUNT_PENDING` / `ACCOUNT_DISABLED` code; wrong credentials still return the generic `INVALID_CREDENTIALS`, so account existence is not disclosed.
- **Owner admin-management API** (owner-only): list admins by status, approve a pending request, reject it, disable/reactivate an admin.
- **Invites**: an owner issues a single-use, expiring, revocable invite token addressed to an email, stored hashed and returned only at issuance as a copyable link (no email sending — the same out-of-band model as team magic links, Phase 5). Registering with a valid invite activates immediately; a spent/expired/revoked invite is rejected.
- **Role enforcement**: owner-only endpoints are guarded by role in the security chain/use cases; a non-owner admin gets a 403 `ProblemDetail` (the 403 path the ownership work left dormant). Status is re-checked on every request, so disabling an admin kills a live session on its next call.
- **Audit**: each account records who approved or invited it, and when.
- Everything transactional; status and role are enforced server-side and never trusted from the client. The operator console, invite entry and pending-login states are built in the Admin frontend phase (Phase 13).

### Acceptance criteria

- [x] A newly registered account starts `PENDING` and cannot log in or reach any `/api/**` route until approved (test) — #21 + #22
- [x] Owner can approve/reject/disable/reactivate admins; a disabled admin's existing session stops working on the next request (tests) — #21 + #22
- [x] Registering with a valid invite activates immediately; the invite is single-use, expiring and revocable (tests) — #23
- [x] Non-owner admin calling an owner-only endpoint gets 403 `ProblemDetail`; unauthenticated gets 401 — the two stay distinct (test) — #22
- [x] Bootstrap produces exactly one owner and offers no open owner-creation path (test) — #21
- [x] Invite tokens are high-entropy, stored hashed, and never returned by any listing API (test) — #23
- [x] Role/status checks live in the use-case layer; wiring stays `@Configuration` + `@Bean` — #21 + #22

---

## Phase 5: Team magic-link access (tickets #24–#26)

**User stories**: As an organizer I issue each team a secret link (and QR), and can revoke/rotate it; as a team member I open the link once and get access to my team's matches for that tournament — and no one else can use or enumerate my token.

### What to build

*Refined in a design session 2026-10-18 — the decisions below are settled (see `CONTEXT.md` for the language).*

- **Team access link**: exactly one per team, **multi-use** — both team members share it as a bearer secret; no per-player identity exists. Auto-provisioned (hashed) when the team is created; the organizer **reveals** it (raw link shown exactly once, with QR on the frontend side), **rotates** it (old link dies immediately, new one revealed once — the documented recovery for a lost link) or **revokes** it. The link stops working once the tournament is `COMPLETED`. Deleting a team removes its token. Tokens are 256-bit random via the existing `SessionTokens` port, stored hashed (`findByTokenHash` only) and never returned by any API other than the one-time reveal — never in any listing (fixes v1's public `access_token` leak).
- **Team login exchange**: the link opens a public route (`/team/login?token=…`), the client exchanges it for an **httpOnly session cookie** (`Secure`, `SameSite=Lax`, set on all cookies via the one shared `SessionCookieFactory`) and strips the token from the address bar. Subsequent team endpoints accept only that cookie — never raw tokens in query strings or headers-as-storage. Team sessions expire after **12h**; admin sessions keep their existing TTL (7d).
- **Team session scope**: the principal is the team (`teamId`, `tournamentId`), authorized for its own team's reads and (once Phase 9 lands) its own confirmations — never other teams' data, never admin endpoints.
- **Rate limiting**: in-memory, per source IP, counting **failed exchange attempts** (e.g. 5 failures / 15 min → temporary block). It must not reveal whether a token exists (identical response for unknown, revoked and rate-limited guesses) and must not be per-token (that would let an attacker lock a team out). Documented as a single-instance assumption (Render free tier is one container); revisit if deployment ever scales out.
- **Credential-lifecycle hygiene**: expired admin and team sessions are removed by an hourly scheduled, idempotent sweep (`DELETE FROM … WHERE expires_at < now`) backed by an index on `expires_at`, so the session tables do not grow without bound. The sweep is safe to run concurrently on every instance.

### Acceptance criteria

- [ ] Tokens are high-entropy, stored hashed, and never returned by any API other than the one-time reveal (test)
- [ ] Exchange endpoint sets an httpOnly session cookie (`Secure`, `SameSite=Lax`); subsequent team endpoints accept only that cookie, never raw tokens in query strings (test)
- [ ] A link is multi-use: both members of a team can exchange it; rotation invalidates the old link immediately; a revoked (or unknown) token gets the same generic rejection; a link no longer exchanges once the tournament is completed (tests)
- [ ] Rate limiting kicks in on repeated failed attempts, does not discriminate unknown vs revoked tokens, and cannot be triggered against one team's token alone (test)
- [ ] Team session cannot access another team's data or admin endpoints (tests)
- [ ] Expired admin and team sessions are purged by a periodic sweep; a test inserts an expired session, runs the sweep, and asserts it is gone while live sessions survive (and that a re-run is safe)

---

## Phase 6: Start tournament: rounds & Swiss pairing

**User stories**: As an organizer I start a tournament with ≥2 teams; round 1 tables are assigned (random), later rounds pair by standings; I see tables and assignments per round. Odd team counts are rejected with a clear error until Phase 7 lands.

### What to build

Tournament status transition `setup → active` (validated: enough teams, correct status), round creation, table assignment with table numbers, and the Swiss pairing algorithm as a pure, unit-testable domain component (standings → pairs, avoiding rematches where possible). Random round-1 shuffle. All of it transactional — a failed start leaves no partial round.

### Acceptance criteria

- [ ] Starting twice, starting with too few teams, starting a non-setup tournament → typed errors, no side effects
- [ ] Round 1 pairing is a uniform random shuffle (test with injected randomness)
- [ ] Swiss pairing unit tests: sorted-by-VP pairing, rematch avoidance, deterministic given input
- [ ] Odd team count → explicit "unsupported" error until Phase 7 (test documents the temporary contract)
- [ ] Partial failure rolls back (test: injected failure leaves database unchanged)

---

## Phase 7: Trio tables (odd team counts)

**User stories**: As an organizer with an uneven number of teams I start the tournament anyway: exactly one table seats 3 teams, the rest pair normally. As a team at the trio table I play 4 of the 6 games played at my table that round and sit out 2, meeting each other team exactly twice.

### What to build

Extend pairing so odd counts produce exactly one trio table (3 teams) plus normal tables for the rest (3 teams → trio only; 5 → trio + 1; 7 → trio + 2). Generate the 6-game sequence per trio table such that every pair appears exactly twice, each team plays 4 and sits out 2, and no team sits twice in a row (e.g. AB, AC, BC, AB, AC, BC). Table/game rows carry which two teams play each game. Pairing remains fully per-round — no cross-round schedule, rematches allowed.

### Acceptance criteria

- [ ] Start with 3, 5, 7, 9… teams: exactly one trio table + correct normal tables (parameterized test)
- [ ] Generated 6-game sequence validated by property tests: every pair twice, 4 played / 2 sitting per team, no consecutive sit-outs
- [ ] Even counts produce no trio table (regression)
- [ ] Round length semantics documented: one tournament round = 4 games at normal tables, 6 at the trio table

---

## Phase 8: Game scoring

**User stories**: As a team member I enter or edit the result of a game (positions, Tichu calls, bombs, base/bonus points) until my table is confirmed; I trust the numbers because the server recomputes everything.

### What to build

The scoring API with full server-authoritative Tichu validation — this is the heart of the app and gets the most exhaustive test suite. Port every rule from v1's 17-case validation script as automated tests: exactly 4 participants / 2 per team / unique positions 1–4; double win (positions 1+2 same team ⇒ base 0, other positions null); base sums to 100; no simultaneous small+grand Tichu; success only with a call and only for position 1; bomb count 0–3; server-recomputed bonuses (±100/±200/+200) must match; victory points computed server-side (2/1/+1 double win). Enforce `settings.allow_grand_tichu`, persist `beschiss`, edits blocked once the table is confirmed. Everything transactional (v1 swallowed participant-insert failures).

### Acceptance criteria

- [ ] Test matrix covers every v1 validation rule plus trio-table games (6 per table, teams switch per game)
- [ ] Client-sent totals are never trusted — server computes and returns authoritative values (test proves mismatch rejection)
- [ ] Edit until confirmation, rejected after (test); concurrent-edit race resolved deterministically (test)
- [ ] Entire submit is atomic — induced failure leaves no orphaned participants (test)
- [ ] Grand Tichu rejected when tournament setting disables it (test)

---

## Phase 9: Confirmation & completion

**User stories**: As a team I review all games at my table and confirm; a normal table needs both teams, a trio table needs all three; I can retract until everyone has confirmed; once complete, games are locked.

### What to build

Per-table confirmation workflow: status progression `playing → confirming → completed` driven by confirmations (2-way for normal tables, 3-way for trio tables), retraction rules (allowed while not fully confirmed; blocked after), locking of games on completion, and the derived per-table completion state consumed by round advancement. Idempotent, race-safe (v1's read-modify-write had no locking).

### Acceptance criteria

- [ ] Normal table: completes exactly when both teams confirmed; trio: exactly when all three (tests for 1/2/3-team partial states)
- [ ] Retract works before full confirmation and is rejected after (tests)
- [ ] Confirmed tables reject scoring edits (test)
- [ ] Simultaneous confirmations from different teams are safe (concurrency test)
- [ ] Wrong team attempting to confirm someone else's table → 403/404 (test)

---

## Phase 10: Standings, next round & finish

**User stories**: As an organizer I advance to the next round when all tables are confirmed, see accurate standings at any time, choose the tournament's total round count, and finish the tournament to get final ranked standings. Finishing is final — no reopen in v2.

### What to build

Standings computation (sum of VP, tie-break total points) as a pure domain function used by both round advancement and final ranking (single implementation — v1 duplicated it). `start-next-round`: verify 100% of tables completed, compute standings, Swiss-pair (with trio handling for odd counts; trio = bottom three teams in standings), create round. `finish-tournament`: final standings with explicit `rank`; a completed tournament cannot be reopened (status transition is one-way). `total_rounds` is an explicit organizer choice — no estimate formula. Full standings incl. per-team games played are correct with trio tables mixed in (each team plays 4 per round regardless of table type).

### Acceptance criteria

- [ ] Standings unit tests: VP sums, tie-breakers, ordering stability
- [ ] Advancement blocked while any table unconfirmed (test); partial state leaves database unchanged (rollback test)
- [ ] Odd-count rounds pair with trio each round — trio teams are the bottom three in standings (test); even rounds pair normally (test)
- [ ] Finish produces final ranks including tie-breaks; exact ties share the same rank (tests)
- [ ] Ownership: only the tournament's owner can advance/finish (test)

---

## Phase 11: SSE live events + statistics

**User stories**: As any user (admin, team, public) I see scores, confirmations, and round changes appear without manual refresh; as a spectator I see real statistics instead of a placeholder.

### What to build

An SSE endpoint authenticated by role (admin/team/public streams scoped to what they may see; the public stream includes game results as soon as they are entered as finished, pre-confirmation) emitting domain events on meaningful changes: score submitted/edited, table confirmed/retracted, round advanced, tournament finished. Events derived from use-case completion (no polling). Plus a statistics endpoint: per-player Tichu success rates, bomb counts, double wins, per-team trends — everything v1's "coming soon" tab promised.

### Acceptance criteria

- [ ] SSE stream delivers events within ~1s of a mutation (integration test)
- [ ] Stream authorization: teams only see their tournament, public stream exposes only public facts (test)
- [ ] Reconnect with Last-Event-ID does not lose or duplicate critical state (test)
- [ ] Statistics endpoint returns correct aggregates for mixed normal/trio tournaments (tests)
- [ ] No unbounded resource leak per connection (connection cleanup on disconnect verified)

---

## Phase 12: Frontend foundation ✅ (done — tickets #9–#13)

**User stories**: As a user I get the app shell in German or English, consistent navigation, and typed API access that fails loudly if the contract drifts.

### What to build

Rebrand the template frontend to the Tichu app: layout/navigation shell, route skeletons for all four areas (admin, team, public, login), i18n infrastructure with German and English catalogs (no hardcoded strings anywhere), the API layer per template conventions (axios instance with auth interceptor stub filled in, zod DTO schemas mirroring backend wire types, mappers, repositories, query hooks, centralized query keys), and the SSE client helper (reconnect, dispatch into query invalidation). Shared UI primitives added to `packages/ui` as needed.

### Acceptance criteria

- [x] All routes reachable in dev; language switch de/en updates every visible string (catalog coverage test/lint rule)
- [x] Repository tests: zod boundary parse rejects a malformed response (template's existing test pattern)
- [x] SSE client reconnects after server restart without user action (test)
- [x] `pnpm typecheck && pnpm lint && pnpm test` green in CI alongside backend

---

## Phase 13: Admin: auth, dashboard, tournament setup (in progress — #14–#16, #20 done; #17–#19 open)

**User stories**: As an organizer I register (and wait to be approved, or use my invite), log in, land on my dashboard, create a tournament, configure it, and manage teams — getting a copyable magic link + QR per team, with the ability to rotate/revoke it. As the platform owner I approve or invite organizers from an admin console.

### What to build

Admin screens end-to-end on the new API: register/login including the **pending-approval state** and invite acceptance, dashboard listing my tournaments, create/edit tournament (settings incl. grand Tichu toggle), setup-phase team management (add/edit/delete with server error surfaced nicely), token link generation + QR display + rotate/revoke actions. For owners, an **admin console**: pending requests to approve/reject, existing admins to disable/reactivate, and invite issuance. Forms via react-hook-form + zod, mutations via TanStack Query invalidation.

### Acceptance criteria

- [ ] Full happy path demoable: register → create tournament → add 4+ teams → see links/QRs  
      _partial: register + dashboard done (#14, #16); create/edit (#17), teams (#18) and links/QR (#19) pending_
- [x] Pending account sees a clear "awaiting approval" state (localized); owner can approve requests and issue invites from the console (component tests)  
      _done: pending/invite states and login gating (#15); owner console (#20)_
- [ ] All v1 validation errors display as localized, field-anchored messages  
      _partial: error-code→de/en mapping, `applyServerError` field/code anchoring and auth messages done (#14); tournament/team errors pending #17/#18_
- [ ] Component tests for the team-add dialog and validation rendering  
      _partial: auth validation rendering covered (#14); team-add dialog pending #18_
- [ ] No token shown after issuance (rotated token replaces, never re-fetchable) — UI matches Phase 5 contract  
      _pending backend Phase 5 (#19)_

---

## Phase 14: Team: login & match overview

**User stories**: As a team member I open my secret link, land in my team's space for the tournament, see my current table, opponents, all games played so far, and the confirmation controls — with live updates when opponents confirm.

### What to build

Magic-link entry flow (exchange once, keep scoped session, never expose raw token in URL after entry), team match overview for the current round: table view with opponents and game results, confirm/retract actions, waiting-state UX that updates live via SSE, error states for revoked/expired links.

### Acceptance criteria

- [ ] Link → exchange → overview works; refreshing keeps the session without re-exposing the token in the URL
- [ ] Revoked link shows a clear localized error screen
- [ ] Confirm/retract reflects instantly on the other team's screen (SSE integration test)
- [ ] Component tests: overview rendering from mocked repository, confirm action states

---

## Phase 15: Game entry (scoring form)

**User stories**: As a team member I enter a game's result with fast controls (finish positions, Tichu calls, bombs, points) and can edit past games until confirmation; on a trio table I pick which of the 6 games I'm entering.

### What to build

The scoring UI: position entry cycling, Tichu call toggles (grand Tichu hidden when disabled), bomb counter, base-score slider with derived complement, auto-derived bonus display — all mirroring (not duplicating) server rules: client computes for UX, server response is authoritative, mismatch surfaces as an error. Edit mode loads an existing game; locked games show read-only state. Trio-aware game selector when at a 3-team table. Local validation gives instant feedback; server rejections render localized messages.

### Acceptance criteria

- [ ] Enter and edit a game end-to-end; double-win auto-handling behaves like v1 but server-verified
- [ ] Trio table: enter any of the 6 games, correct opponents shown per game
- [ ] Confirmed table → form read-only with explanation
- [ ] Component tests: validation messages, double-win state transitions, edit-mode loading; mapper/repository tests for the scoring payload

---

## Phase 16: Tournament management live

**User stories**: As an organizer I start the tournament, follow every round (tables, scores, confirmations, standings), advance rounds, choose total rounds, finish and reopen — all updating live without refreshing.

### What to build

Admin tournament detail: status card with guarded actions (start/advance/finish/reopen), rounds accordion with table assignments (incl. trio tables visibly marked with 3 teams and 6 games), standings tab with tie-break sorting and correct VP totals, settings dialog. All mutations via API, all views subscribed to SSE events → query invalidation. Advance action blocked with clear messaging while confirmations are pending.

### Acceptance criteria

- [ ] Full lifecycle demoable: setup → start → several rounds (incl. one odd-count round with trio) → finish → final ranking, with live refresh at each step
- [ ] Advancing with pending confirmations shows a localized explanation, not a raw error
- [ ] Standings match backend computation exactly (shared fixture test comparing API response to UI render)
- [ ] Component tests for status actions and rounds/standings rendering

---

## Phase 17: Public views, statistics & ship

**User stories**: As a spectator I follow a tournament without logging in — standings, matches, statistics — in my language, and everything is deployed so anyone with the URL can use it.

### What to build

Public tournament pages: standings, per-round matches (normal + trio tables), live statistics (Tichu success, bombs, double wins, player highlights), live badge via SSE; public home listing active tournaments (scoped public data only). Game results are shown as soon as entered as finished, including before table confirmation. Final i18n sweep (v1's mixed German/English leftovers must not recur — enforced by catalog coverage), accessibility and responsive pass on core flows, and the delivery pipeline: production build, Docker image, CI deploy to the free-tier combo (container platform + managed free Postgres) with environment documentation. Hosting and delivery are broken out step by step in [`deployment.md`](./deployment.md).

### Acceptance criteria

- [ ] Public pages render correct data for a completed tournament incl. trio rounds and statistics
- [ ] i18n coverage check: no untranslated keys, no hardcoded strings in production paths
- [ ] Public API surface exposes only public facts (no team tokens, no session/credential material; finished-but-unconfirmed game results are intentionally public — security test)
- [ ] One-command deploy documented; CI builds, tests, and deploys; production URL reachable
- [ ] README covers architecture, local dev, deployment, and the domain rules (Tichu scoring, Swiss pairing, trio tables)

---

## Explicitly out of scope (v2)

- Reopening a completed tournament (revisit later)
- Smarter trio-table team selection (v2 = bottom three in standings; revisit later if the same teams get triod repeatedly)
- Import of v1 data
- Manual pairing override by organizer (revisit later)
- CSV/print export (revisit later)
- Multiple admins per tournament (revisit later)
- Real-time card/hand tracking (app remains a post-game scorebook)
