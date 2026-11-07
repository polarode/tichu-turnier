# 0007 — Live events are thin invalidation signals, with no replay

Date: 2026-11-07 · Status: accepted · Context: issue #36 (S8), public stream in #37 (S9)

## Decision

An SSE event says only **what changed and where**: its type plus the tournament,
round and table ids (e.g. `TABLE_CONFIRMED`, round 2, table 3). It carries no scores,
standings or other data. On an event the client invalidates the matching queries and
refetches through the ordinary read endpoints.

There is **no replay buffer**. After a reconnect the client invalidates all of the
tournament's queries once. Event ids are still sent, but only for debugging.

## Why

- **Authorization stays in one place.** Every read endpoint already decides who may
  see what (standings are organizer-only, the Final Ranking waits for publication).
  A payload event would have to repeat that filtering per role and per event type,
  and a mistake there leaks results before publication. A thin signal reveals
  nothing a refetch would not, so a stream only has to be scoped to the tournament.
- **Reconnect is a refetch.** Invalidating is idempotent, so a lost or duplicated
  event can't corrupt state. A Last-Event-ID buffer would add code for the same
  outcome, and would be lost on every restart anyway.

## Considered options

- *Full payload events* (clients patch their cache). Rejected: duplicates the
  authorization and couples the stream to every read model's shape.
- *Last-Event-ID replay from a bounded in-memory buffer.* Rejected as above.

## Consequences

- Each event causes one refetch per affected view. That is fine at tournament scale
  (tens of clients), and the price of keeping the stream dumb.
- The public stream carries only lifecycle events (round started, tournament
  finished, results published). Score and confirmation events change nothing a
  spectator can see.
