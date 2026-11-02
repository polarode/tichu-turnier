# 0006 — Confirmation is per table and freezes games at the first confirmation

Date: 2026-11-02 · Status: accepted · Context: issue #34 (S6), supersedes the per-round lock of #100 (S5b)

## Decision

Confirmation is recorded **per table, per team**, as one row per confirming team.
The **Table Status** is derived from those rows, never stored:

- **Playing**: no team confirmed. Games are editable.
- **Confirming**: at least one team confirmed. Games are **frozen for every team**
  at the table. Retracting the last confirmation returns the table to Playing.
- **Completed**: every team seated at the table confirmed. Final, no retraction.

A team may only confirm once all of the table's games (4, trio 6) are entered.
A round is complete when all its tables are completed; round advancement (S7)
consumes that derived fact. S5's per-round `rounds.locked` boolean is dropped.

## Why

- **Per table, not per round.** Tables finish at different times. A per-round lock
  would leave finished tables editable while waiting for the slowest one, and the
  confirming parties are the teams at one table, not the whole round.
- **Freeze at the first confirmation, not at completion.** Otherwise a team could
  confirm results that the other team then changes, and completion would lock in
  games the first team never saw. Freezing at the first confirmation means every
  confirmation covers exactly the games that end up final. To correct a game,
  the confirmed team retracts first, which is a deliberate, visible step.
- **Derived status.** Consistent with ADR 0004: store the raw facts (who confirmed),
  derive the state. No status column can drift from the confirmations.

## Considered options

- *Keep the per-round lock* (S5's choice), with confirmations only flipping it once
  all tables are done. Rejected for the reasons above.
- *Edits allowed while Confirming, clearing all confirmations.* Rejected: it lets an
  unconfirmed team silently undo the other team's confirmation.

## Consequences

- Teams see the frozen state as soon as anyone at their table confirms. The UI
  must explain it ("waiting for the other team(s)", retract to edit).
- A team that never confirms blocks its table, and so the round. There is no admin
  override in S6. That is a known follow-up.
- Game edits, confirm and retract on one table must be serialized, because a
  confirm racing an edit would otherwise confirm a stale game.
- A confirm carries the game versions it confirmed, so it never covers a game
  the team didn't see (an edit that landed after the team's last read).
- Edits to a frozen table keep answering `GAME_CONFIRMED`, in both Confirming and
  Completed.
