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

### Serving

**SPA Fallback**:
Serving `index.html` for any unmatched non-API path, so client-side routes survive
a browser refresh. Unknown API paths never fall back; they keep returning a JSON
problem response.
_Avoid_: catch-all route, rewrites

**Health Endpoint**:
The unauthenticated `GET /health` outside the API namespace that reports whether
the app is up and can reach its database. A failing database yields an error
status without revealing internals.
_Avoid_: status page, ping
