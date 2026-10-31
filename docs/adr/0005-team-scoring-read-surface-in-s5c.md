# 0005 — The team's scoring read surface lives in S5c, not S5b

Date: 2026-10-31 · Status: accepted · Context: issues #100 (S5b), #101 (S5c)

## Decision

The two team-scoped **read** endpoints that the scoring form depends on are built
as part of **S5c (the team scoring form, #101)**, not S5b (the scoring API, #100).
S5b delivered only the write surface — `POST`/`PUT .../games` — plus the identity
read `GET /api/team/me`. A team session had no way to discover what it needs to
drive the form, so S5c adds:

- **`GET /api/team/me/current-table`** → the single in-progress round the team is
  seated at: `roundNumber`, `tableNumber`, `locked`, `allowGrandTichu`, and the
  table's `teams` (each with `teamId`, `teamName`, and its players' ids + names).
  One object; "current" is the team's one active round (one round open at a time).
- **`GET /api/team/tournaments/{tournamentId}/rounds/{roundNumber}/tables/{tableNumber}/games`**
  → `{ games: RecordedGameDto[] }`, reusing the exact `RecordedGameDto` the write
  endpoints already return. A game number absent from the list means "not yet
  entered".

These endpoints are team-authorized (`TEAM_SESSION_AUTHORITY`) and scoped to the
caller's own team/table, following the existing seat-check pattern.

## Why

- **The form cannot function without them.** The write endpoints address a game by
  `(tournamentId, roundNumber, tableNumber, gameNumber)`, but a team session only
  knew its `tournamentId` and `teamId` — not its round or table, not its opponent,
  not the `allow_grand_tichu` setting, and had no way to reload an already-entered
  game to get the `version` a `PUT` requires. Three of #101's acceptance criteria
  (edit-mode loading, read-only locked form, the `GAME_ALREADY_EXISTS` → load-existing
  flow) are unsatisfiable without a read path.
- **The only existing teams→tables mapping is admin-only** (`GET /api/tournaments/{id}/rounds`),
  and opening it to teams would leak the whole tournament's pairing to every team.
  A narrow, self-scoped team read is the right boundary.
- **Cohesion over slice purity.** The two reads are small, hexagonal, and form one
  idea — "the team's read surface for scoring". Folding them into S5c keeps that
  idea in one slice rather than splitting a trivial backend ticket out and stalling
  the form behind it.

## Consequences

- S5c is **not** a frontend-only slice: it carries a backend increment (two use
  cases + adapters + REST handlers + tests). This is recorded as a scope note on the
  S5 parent (#33) without rewriting #100's or #33's body — S5b remains "done" as it
  was scoped.
- `RecordedGameDto` is now a **shared read/write response shape**: the frontend has
  a single mapper for both the write response and the list read. A future change to
  that DTO touches both paths at once.
- `current-table` assumes exactly one active round per team at a time. If rounds can
  ever be concurrently open for a team, "current" becomes ambiguous and the endpoint
  must take a round selector or return a list — revisit then.
- Per-team confirmation indicators ("Team X confirmed") are **not** part of this
  read surface: S5c exposes only the round's existing `locked` boolean. The per-team
  confirmation model and its UI belong to S6 (#34), which owns the confirm/retract
  workflow.
- Trio-aware, per-game matchup labels are out of scope here: `current-table` returns
  the two teams of a normal table and the form shows a single matchup label. The
  per-game rotation lands with the trio-table slice (#32).
