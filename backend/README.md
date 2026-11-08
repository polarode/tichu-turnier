# Backend — Kotlin + Spring Boot

Hexagonal (ports & adapters) architecture. See the root [`AGENTS.md`](../AGENTS.md)
for the full convention set.

## Run

```bash
./gradlew bootRun     # http://localhost:8080
./gradlew test        # tests
./gradlew build       # compile + test + jar
```

Requires a JDK 21+ on the machine; the Gradle wrapper handles the rest.

## API

| Method | Path                     | Description                            |
|--------|--------------------------|----------------------------------------|
| GET    | `/api/tournaments`       | list the caller's tournaments          |
| GET    | `/api/tournaments/{id}`  | get one tournament (404 for foreign ids) |
| POST   | `/api/tournaments`       | create a tournament                    |
| PUT    | `/api/tournaments/{id}`  | replace a tournament's configuration   |
| DELETE | `/api/tournaments/{id}`  | delete a tournament and its scoped data |
| POST   | `/api/tournaments/{id}/start` | start the tournament and draw the first round |
| GET    | `/api/tournaments/{id}/rounds` | list rounds with their tables, seating and entered games |
| POST   | `/api/tournaments/{id}/rounds` | pair and start the next round          |
| GET    | `/api/tournaments/{id}/standings` | current standings                   |
| PUT    | `/api/tournaments/{id}/total-rounds` | adjust the planned number of rounds |
| POST   | `/api/tournaments/{id}/finish` | finish the tournament after its last round |
| POST   | `/api/tournaments/{id}/publish` | publish the results (reveal the final ranking to teams) |
| GET    | `/api/tournaments/{id}/events` | SSE stream of tournament activity (see *Live events*) |
| GET    | `/api/tournaments/{tournamentId}/teams` | list a tournament's teams |
| GET    | `/api/tournaments/{tournamentId}/teams/{teamId}` | get one team with its players |
| POST   | `/api/tournaments/{tournamentId}/teams` | create a team (name + two players) |
| PUT    | `/api/tournaments/{tournamentId}/teams/{teamId}` | rename a team and/or swap its players |
| DELETE | `/api/tournaments/{tournamentId}/teams/{teamId}` | delete a team and its players |
| POST   | `/api/tournaments/{tournamentId}/teams/{teamId}/access-link/reveal` | reveal the team's access link once |
| POST   | `/api/tournaments/{tournamentId}/teams/{teamId}/access-link/rotate` | issue a fresh access link |
| POST   | `/api/tournaments/{tournamentId}/teams/{teamId}/access-link/revoke` | revoke the team's access link |

All of these endpoints require a valid admin session. New tournaments start in
status `setup`; the machine is `setup → active → completed → published`, one-way.
Starting draws the first round (and needs enough teams — `409 INSUFFICIENT_TEAMS`
otherwise); the next round pairs teams by standings, avoiding rematches, and is only
allowed once every table of the current round is complete. A tournament's
configuration can only be edited while it is in `setup`; an edit against a started
tournament answers `409 TOURNAMENT_NOT_EDITABLE`. Deleting is allowed in any status
and cascades to everything scoped to the tournament. An access link is revealed at
most once (`409 LINK_ALREADY_REVEALED` afterwards); rotating issues a new token and
invalidates the old one.

Teams are tournament-scoped and validated server-side: team names are compared
case-insensitively and must be unique per tournament, while player names only need
to be different within one team (the two players of a team must be different
people). Field and uniqueness violations answer `409` with a stable `code`
(`DUPLICATE_TEAM_NAME`, `DUPLICATE_TEAM_PLAYERS`) and a `field` member. Creating a team when the tournament
already holds its `maxTeams` answers `409 TEAM_LIMIT_REACHED`; lowering `maxTeams`
below the registered team count answers `409 MAX_TEAMS_BELOW_TEAM_COUNT` anchored to
`maxTeams`. Player names may repeat across teams and tournaments. Creating,
editing and deleting a team answers `409 TOURNAMENT_NOT_EDITABLE` once the tournament
has left `setup`.

Example:

```bash
curl -X POST localhost:8080/api/tournaments \
  -H 'Content-Type: application/json' \
  -d '{"name":"Spring Classic","description":"annual invitational","maxTeams":16,"settings":{"grandTichuBonus":200,"doubleWinBonus":200}}'
```

### Auth (admin accounts)

| Method | Path                 | Description                                        |
|--------|----------------------|----------------------------------------------------|
| POST   | `/api/auth/register` | request an admin account (or activate at once with an invite token) |
| POST   | `/api/auth/login`    | exchange credentials for an httpOnly session cookie |
| POST   | `/api/auth/refresh`  | rotate a valid session cookie                      |
| POST   | `/api/auth/logout`   | invalidate the session and clear the cookie        |

Registration is a *request*: it creates a `PENDING` account that cannot log in or
reach any `/api/**` route. Only once an owner approves it (see *Admin management*
below) does it become `ACTIVE` — unless the request carries a valid `inviteToken`
(from an owner-issued invite link), in which case the account is created `ACTIVE`
straight away and the invite is spent. Login verifies the password first,
then the account status: correct credentials on a pending or disabled account answer
`403 ACCOUNT_PENDING` / `403 ACCOUNT_DISABLED`, while wrong credentials stay the
generic `401 INVALID_CREDENTIALS`, so account existence is never disclosed. Status is
re-checked on every request, so disabling an admin stops its live session on the next
call.

The first platform owner is provisioned idempotently at startup from
`app.auth.owner.email` + `app.auth.owner.password-hash` (a bcrypt hash — see the root
README). There is no endpoint that creates or promotes an owner.

Sessions use the `admin_session` httpOnly `Secure` `SameSite=Lax` cookie; the raw
token is never returned in a response body. Error responses are RFC 7807 problem
JSON carrying a stable, machine-readable `code`. The full set is the `ErrorCode` enum
in `vocabulary/Exceptions.kt` (auth and account codes such as `ACCOUNT_PENDING`,
`INVALID_CREDENTIALS`, `UNAUTHENTICATED`, `FORBIDDEN`, `ADMIN_STATUS_CONFLICT`;
tournament, team and round codes such as `TOURNAMENT_NOT_EDITABLE`,
`DUPLICATE_TEAM_NAME`, `TEAM_LIMIT_REACHED`, `TOURNAMENT_NOT_STARTABLE`,
`ROUND_INCOMPLETE`, `RESULTS_NOT_PUBLISHED`; invite and access codes such as
`INVITE_INVALID`, `TEAM_ACCESS_INVALID`; and game/table codes such as
`GAME_VALIDATION_INVALID`, `GAME_CONFIRMED`, `TABLE_INCOMPLETE`); the frontend mirrors
them in `vocabulary/error-code.enum.ts`. Cookie lifetime and the `Secure` flag are
configured under `app.auth` in `application.yml`.

```bash
# register a request: the account starts PENDING and cannot log in yet
curl -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"organizer@example.com","password":"s3cret-pw"}'

# log in as an already-approved admin (e.g. the configured owner)
curl -c cookies.txt -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"organizer@example.com","password":"s3cret-pw"}'

curl -b cookies.txt -c cookies.txt -X POST localhost:8080/api/auth/refresh
```

### Admin management (owner-only)

| Method | Path                            | Description                                          |
|--------|---------------------------------|------------------------------------------------------|
| GET    | `/api/admins`                   | list accounts, optionally `?status=PENDING\|ACTIVE\|DISABLED` |
| POST   | `/api/admins/{id}/approve`      | activate a pending request, recording approver + time |
| POST   | `/api/admins/{id}/reject`       | delete a pending request                             |
| POST   | `/api/admins/{id}/disable`      | disable an active admin (kills its live session)     |
| POST   | `/api/admins/{id}/reactivate`   | restore a disabled admin to active                   |

Every endpoint is owner-only. The role is enforced in the use-case layer from the
session principal: a signed-in non-owner answers `403 FORBIDDEN`, while an anonymous
caller answers `401 UNAUTHENTICATED` — the two stay distinct. Mutations that do not
match the target's current status answer `409 ADMIN_STATUS_CONFLICT`; an unknown
target answers `404 NOT_FOUND`; the platform owner account itself cannot be disabled.
Responses are built from a read model with no password-hash field, so no listing can
ever return credential material.

```bash
# list the pending registration requests
curl -b cookies.txt 'localhost:8080/api/admins?status=PENDING'

# approve one
curl -b cookies.txt -X POST localhost:8080/api/admins/<id>/approve
```

### Invites (owner-only)

| Method | Path                       | Description                                  |
|--------|----------------------------|----------------------------------------------|
| POST   | `/api/invites`             | create an invite with a free-text label      |
| GET    | `/api/invites`             | list invites with their status               |
| POST   | `/api/invites/{id}/revoke` | revoke a pending invite                       |

An invite carries a one-time token that the owner shares as a link. Registering with
that token activates the new account immediately (see *Auth*). An invite is `PENDING`
until it is spent or revoked; acting on an invite that is not pending answers
`409 INVITE_STATUS_CONFLICT`, and an unknown or expired token answers `INVITE_INVALID`
/ `INVITE_EXPIRED` at registration.

### Team sessions (access-link auth, scoring, confirmations)

Teams never have an admin account; they authenticate with the access link the
organizer gives them and get their own `team_session` cookie. These routes live under
`/api/team/**` and require a team session (not an admin one).

| Method | Path                                                                             | Description                               |
|--------|----------------------------------------------------------------------------------|-------------------------------------------|
| POST   | `/api/team/auth/exchange`                                                        | exchange an access link for a team session |
| GET    | `/api/team/me`                                                                   | the signed-in team's overview             |
| GET    | `/api/team/me/current-table`                                                     | the team's table in the current round     |
| GET    | `/api/team/me/final-ranking`                                                     | the final ranking (once published)        |
| GET    | `/api/team/tournaments/{t}/rounds/{r}/tables/{n}/games`                          | the games entered at the team's table     |
| POST   | `/api/team/tournaments/{t}/rounds/{r}/tables/{n}/games`                          | record a game                             |
| PUT    | `/api/team/tournaments/{t}/rounds/{r}/tables/{n}/games/{gameNumber}`             | correct a saved game                      |
| PUT    | `/api/team/tournaments/{t}/rounds/{r}/tables/{n}/confirmation`                   | confirm the table's results               |
| DELETE | `/api/team/tournaments/{t}/rounds/{r}/tables/{n}/confirmation`                   | retract a confirmation                    |
| GET    | `/api/team/events`                                                               | SSE stream of the team's tournament (see *Live events*) |

Games are validated server-side and derived from raw player facts (ADR 0004); a
confirmed table is frozen at the first confirm (ADR 0006), so editing a game on it
answers `409 GAME_CONFIRMED`. A table's status (`PLAYING → CONFIRMING → COMPLETED`)
is derived, never stored. The final ranking is only served after the tournament is
published; before that it answers `409 RESULTS_NOT_PUBLISHED`.

### Live events

Two Server-Sent-Events streams (`text/event-stream`) carry thin invalidation signals
(ADR 0007): only the event type and the tournament, round and table ids, never scores
or standings. Clients refetch through the read endpoints above, which keep enforcing
who may see what. An event is sent when a game is recorded, a table is confirmed or
retracted, the tournament or a round starts, the total rounds change, the tournament
finishes, or its results are published.

- `GET /api/tournaments/{id}/events`: the organizer's stream, admin session, own
  tournaments only.
- `GET /api/team/events`: the team's stream, team session. Its tournament comes from the
  session, so a team only ever receives events of its own tournament.

There is no replay buffer: after a reconnect the client refetches everything once. A
heartbeat keeps the connection alive about every 20 seconds.

## Layers

- `domain` — entities and value types (`Tournament`/`TournamentStatus`/
  `TournamentSettings`, `Team`/`Player`, `Round` + `SwissPairing`, `Game`/`GameKey` +
  the `GameScoring` engine, `TableConfirmations`, `Standings`, `AdminUser`/
  `AdminInvite`/`AdminSession`, `TeamSession`/`TeamToken`, and the enums) plus the
  repository ports (`TournamentRepository`, `TeamRepository`, `RoundRepository`,
  `GameRepository`, `TableConfirmationRepository`, `AdminUserRepository`,
  `AdminInviteRepository`, `AdminSessionRepository`, `TeamSessionRepository`,
  `TeamTokenRepository`) and the other ports (`TournamentEvents`, `SessionTokens`,
  `PasswordHasher`, `IdGenerator`). No Spring.
- `application/api` — inbound ports (use-case interfaces), one per use case: the admin
  auth/session, admin-management and invite cases; the tournament cases
  (`CreateTournament`, `UpdateTournament`, `DeleteTournament`, `GetTournament`,
  `ListTournaments`, `StartTournament`, `StartNextRound`, `SetTotalRounds`,
  `FinishTournament`, `PublishTournament`, `GetStandings`, `ListRounds`); the team
  setup and access-link cases; and the scoring cases (`RecordGame`, `ConfirmTable`,
  `GetTeamScoring`, `GetOwnTeam`, `GetFinalRanking`).
- `application/usecase` — implementations + `UseCaseConfiguration` (the `@Bean` wiring).
- `infra/persistence` — a JPA adapter per repository port (`JpaTournamentRepository`,
  `JpaTeamRepository`, `JpaRoundRepository`, `JpaGameRepository`,
  `JpaTableConfirmationRepository`, the admin/session/token adapters), each wrapping a
  Spring Data repository over its JPA entity, plus `UuidIdGenerator`,
  `ExpiredSessionSweeper` and `PersistenceConfiguration`. `InMemoryTournamentRepository`
  stays as a fast fake for use-case unit tests (the rest of the in-memory fakes live
  under `src/test`).
- `infra/security` — `BcryptPasswordHasher`, `SecureSessionTokens`, the admin and team
  session authentication filters, the problem-detail security handlers +
  `SecurityConfiguration`.
- `infra/rest/web` — the controllers (`TournamentController`, `TeamController`,
  `AuthController`, `AdminController`, `InviteController`, `TeamAuthController`,
  `TeamScoringController`, `GameController`, `TableConfirmationController`,
  `FinalRankingController`, `TournamentEventsController`, `TeamEventsController`), their DTOs, the
  session-cookie factory, the SPA fallback (`SpaWebConfiguration`), error advice +
  `RestConfiguration`.
- `infra/events` — `SseTournamentEventBroadcaster` (the `TournamentEvents` adapter), its
  `TournamentEventHeartbeat` and `EventsConfiguration`.
- `main` — `Application` composition root + `BootstrapConfiguration` (the owner
  provisioning runner).

The production adapter is JPA over PostgreSQL with the schema managed by Flyway
(`src/main/resources/db/migration`). To swap the storage technology again, write
a new adapter implementing the relevant repository port and change the bean in
`PersistenceConfiguration`. Nothing else changes.
