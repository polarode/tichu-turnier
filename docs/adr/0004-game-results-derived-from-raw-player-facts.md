# 0004 — Game results are derived from raw player facts

Date: 2026-10-30 · Status: accepted · Context: issue #33 (S5 — Scoring)

## Decision

The server persists only **raw facts** of a game — per-player finish position,
Tichu call (none/small/grand), bomb count, the two base scores, the Beschiss flag —
and **derives** everything else at write time:

- **Tichu success** = a call was made **and** the player finished first (position 1).
- **Double win** = the players holding positions 1 and 2 belong to the same team;
  it forces base scores 0/0 and leaves the losing players' positions unset.
- **Bonuses and total Tichu Points (TP)** = base ± 100/± 200 (small/grand Tichu,
  success-dependent) + 200 for a double win, recomputed by the server.
- **Victory Points (VP)** = comparison of the two teams' TP (win 2 / tie 1),
  plus 1 extra for the double-win team.

Client-sent totals, success flags and double-win booleans are **never trusted and
never persisted**; in fact client totals are not submitted at all (see Amendment
below). Bonus amounts and the grand-Tichu toggle (`settings.allow_grand_tichu`)
are server configuration, not per-request input.

> **Amendment 2026-10-31** (issue #99 grilling): the payload carries raw facts
> only — no client-computed totals — so the proposed mismatch validation error
> and its error path are dropped. Client-side math stays display-only; the
> client never sends totals, so there is nothing to mismatch.

## Why

- v1 (`tichu-score-tracker`) accepted client-sent `tichu_success` and `total_score`
  values and stored them as-is; wrong or stale client math became stored fact.
- Once statistics (S9) and standings (S7) consume games, retro-fixing stored
  derivations is expensive; keeping one derivation path (server, pure domain
  component) makes the recorded game self-consistent by construction.
- The scoring form still computes the same math client-side for instant feedback,
  but it is display-only and never submitted (amendment above).

## Consequences

- The scoring engine is a pure, unit-testable domain function: raw facts →
  derived outcome; the v1 17-case validation matrix ports directly onto it.
- Schema stores raw facts only; adding a derived metric later (e.g. new bonus
  rules) requires no data migration of past games' facts.
- A legit rule change to bonus amounts changes derived values retroactively for
  already-persisted games unless amounts are snapshotted at submit time — accepted
  for v2; amounts are treated as constants of the game, not configuration.
