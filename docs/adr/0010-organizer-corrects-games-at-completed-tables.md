# 0010 — An organizer may correct a game, but only once its table is completed

Date: 2026-11-10 · Status: accepted · Context: follow-up to ADR 0006 ("no admin override")

## Decision

An organizer (the lead or a co-organizer) may **correct** a recorded game, but only
at a **Completed** table and only while the tournament is **Active** or
**Completed**. Teams keep the only write path at Playing tables; Confirming and
Completed tables keep answering `GAME_CONFIRMED` to them (ADR 0006).

- A correction replaces the game's raw facts like a team edit does (ADR 0004): same
  validation, same optimistic version check, outcome re-derived.
- It stamps who corrected the game and when (`corrected_by`, `corrected_at`). The
  stamp is shown on the game, so teams can see that the organizer changed a result
  they had confirmed.
- Standings, statistics and the Final Ranking are computed on read, so they pick up
  the correction without further writes. Pairings of rounds already drawn are
  stored and stay as drawn.
- A correction is refused before the table is completed (`TABLE_NOT_COMPLETED`) and
  once the tournament is published (`TOURNAMENT_NOT_ACTIVE`).

## Why

- **Last resort, not a second editing path.** Before completion the teams can still
  fix their own games (retract, edit, confirm again). Letting the organizer write
  there too would race the teams and undercut their confirmations.
- **Not after publishing.** The Final Ranking has been announced; changing it then
  would contradict what teams and spectators were shown.
- **Visible audit.** Teams confirmed the old result. The stamp makes the change
  traceable instead of silent.

## Consequences

- The organizer's UI warns that rounds already drawn keep their pairings.
- `corrected_by` has no foreign key, like the other audit columns (`approved_by`,
  `invited_by`): the audit fact outlives the admin account.
- A team's live stream gets the usual `GAME_RECORDED` signal, so open overviews
  refresh.
