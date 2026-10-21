# Tichu Turnier

A tournament management app for Tichu events: organisers run tournaments, register
teams of two players each, and hand out magic links so players can join.

## Language

### Tournament lifecycle

**Setup Phase**:
The tournament status in which teams may be managed (added, edited, deleted) and
settings edited. Once the tournament leaves it, both are locked.
_Avoid_: preparation, draft mode

### Teams

**Team**:
A named pair of exactly two distinct players within one tournament.
_Avoid_: group, pair

**Player**:
A named participant belonging to exactly one team of a tournament.
_Avoid_: member, user

**Access Link**:
The secret per-team link that lets a team's players log in. Its token is shown
to the organizer exactly once (Reveal), replaced by Rotate, disabled by Revoke.
_Avoid_: invite, share link

**Team Capacity**:
The tournament-level maximum number of teams (`maxTeams`). Reaching it only prevents
adding teams; editing and deleting remain possible.
_Avoid_: limit, team limit
