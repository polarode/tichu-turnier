# 0011 — A withdrawal keeps played games and turns owed games into forfeits

Date: 2026-11-14 · Status: accepted · Context: amends ADR 0006 and ADR 0010 (finality of
Completed tables and Published results)

## Decision

An organizer (the lead or a co-organizer) may **withdraw** a team at any status after the
Setup Phase, even once published, as either **Disqualified** or **Voluntary**.

- Games the team already played **stand**. Its opponents keep what they earned.
- Games it still owed at its current table become **Forfeit Games** for the remaining team,
  at the **Forfeit Compensation** the organizer picks for that withdrawal: full (2 VP,
  100 TP), half (1 VP, 50 TP) or none. They count toward VP and TP only, never as played
  games in the statistics. At a Trio Table, only the withdrawn team's games forfeit; the
  other two teams still play each other.
- The withdrawn team's confirmation is no longer needed, so its table can complete.
- It is not drawn into later rounds. The next round cannot be drawn with fewer than two
  active teams.
- In the Standings, a disqualified team is listed last without a rank, and a team that
  withdrew voluntarily keeps its rank, marked. Everyone sees the reason.
- Its Access Link turns read-only.
- Undo is possible until the next round is drawn, or until publishing if no round follows.
  It removes the Forfeit Games and returns the table to Playing, even if it was Completed.

> **Amendment 2026-11-15**: undo only reopens a table when the withdrawal produced Forfeit
> Games there; a table without any keeps its confirmations, so it waits for the returning
> team's confirmation at most. In a Completed tournament, undo is refused when it would
> leave the table unfinished (it produced Forfeit Games, or the team had not confirmed),
> because the tournament cannot return to Active.

## Why

- **Keep played games rather than void them.** Voiding would take points away from the
  teams that beat the withdrawn team and leave rounds uneven. Standings have no
  opponent-strength tie-breaker, so a kept game affects only the two teams that played it.
- **Compensation per withdrawal.** A fixed rule fits badly: full points can over-reward
  the opponent and none punishes them for something they didn't do. The organizer judges
  the case.
- **Even after publishing.** Cheating can come out after the results are announced, and the
  organizer must still be able to act on it. That is a deliberate exception to ADR 0010's
  "not after publishing", and so a withdrawal made then is final at once.
- **Undo reopens a Completed table.** A misclicked withdrawal must be fixable while
  nothing has been built on it. Until the next draw, only that table depends on it.

## Considered options

- Void all of the team's games — rejected (it punishes past opponents).
- Allow withdrawal only between rounds — rejected (it would leave a table half played with a
  cheater who never confirms).
- A fixed forfeit score — rejected in favor of the organizer's choice.
