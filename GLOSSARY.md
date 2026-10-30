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

### Rounds & standings

**Round**:
One iteration of the tournament: every team is seated at exactly one table and
plays four games there. A normal table plays its four games between the same two
teams; a trio table plays six games (a sequence in which every pair of the three
teams meets twice), so each of its teams plays four and sits out two.
_Avoid_: game, match

**Game**:
One completed deal of Tichu at a table, recorded as the four participating
players' finish order, Tichu calls, bomb counts and the two teams' base scores.
A normal table hosts four games per round, a trio table six.
_Avoid_: match, round (when speaking of a single deal)

**Position**:
A player's finish order within one game (1 = first out). In a double win the two
winning players hold positions 1 and 2; the two losing players have no position —
the game ended without them finishing.
_Avoid_: rank, placement

**Pairing**:
The assignment of teams to opponents for a round, produced by the Swiss pairing
component. Each pairing assigns every team to exactly one numbered table.
_Avoid_: draw, bracket

**Rematch**:
A pair of teams meeting more than once across rounds; avoided where possible.
_Avoid_: repeat, replay

**Victory Points (VP)**:
The primary standings score awarded per game result: win 2, tie 1, plus 1 bonus
for a double win.
_Avoid_: points (ambiguous with Tichu points)

**Tichu Points (TP)**:
The card points a team accumulates from playing games (base scores summing to
100 plus Tichu bonuses); the secondary standings tie-breaker behind VP — winning
with a bigger TP margin therefore ranks a team higher on equal VP.
_Avoid_: points, score

### Scoring

**Beschiss**:
A per-game flag meaning "someone cheated — sort of": the loose, playful Tichu-table
accusation, not a formal ruling. Set by the teams when entering the game; carries no
scoring consequence.
_Avoid_: cheating (too grave), foul

**Tichu Call**:
A player's pre-deal announcement: none, small Tichu (after first card) or grand
Tichu (before any card). Success is derived: called and finished first. Grand Tichu
may be disabled by the tournament settings.
_Avoid_: announcement, bet

**Base Score**:
The card points a team collects in a game before bonuses; the two teams' base scores
sum to 100. The only score a team enters — bonuses and totals are derived.
_Avoid_: raw score, card points

**Double Win**:
A game outcome where one team's two players finish first and second; the base scores
are 0/0 and the other team's players have no position. The winning team earns a
+200 bonus and an extra victory point.
_Avoid_: sweep, shutout

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
