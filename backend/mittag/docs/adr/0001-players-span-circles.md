# 0001 — Players span Circles and have globally unique names

Date: 2026-11-25 · Status: accepted

## Decision

A mittag deployment hosts many Circles, and a Player is one record across all of them:
it takes part in Circles through Circle Memberships, its name is unique across the
whole deployment, and a Circle Admin can invite an existing Player into their Circle by
name. Statistics inside a Circle cover that Circle only; a Player's cross-Circle
statistics are visible only to the Account linked to that Player.

## Considered options

- **A Player per Circle, linked through the Account.** Names would only be unique per
  Circle and nothing would leak between Circles, but a person playing in two Circles
  would be two Players, and anyone without an Account could never be recognised as
  the same person.
- **One Circle per deployment.** The simplest model, but a family circle or another
  office would need its own deployment.

## Why

The same people play in several circles (at lunch, in the family), and their history
should be one history. One Player record makes that natural and keeps merging and
renaming in one place.

## Consequences

- Name collisions across Circles are real: two different Maxes in unrelated Circles
  cannot both be "Max".
- Inviting by name means a Circle Admin can learn that a name exists elsewhere. This
  was accepted as the price of easy cross-Circle play.
- Archiving is per Circle Membership, and merging duplicates is limited to a Circle
  Admin's own Circles, because both act on one shared Player.
