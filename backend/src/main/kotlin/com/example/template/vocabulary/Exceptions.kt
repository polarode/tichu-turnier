package com.example.template.vocabulary

/**
 * Stable, machine-readable error codes returned in the `code` member of RFC 7807
 * problem responses. The server speaks English only; the frontend localizes by
 * branching on these codes, so they must never be renamed without a frontend change.
 */
enum class ErrorCode {
    NOT_FOUND,
    INVALID_INPUT,
    EMAIL_ALREADY_REGISTERED,
    INVALID_CREDENTIALS,

    /** Correct credentials on an account that is still awaiting approval. Mapped to HTTP 403. */
    ACCOUNT_PENDING,

    /** Correct credentials on an account that has been disabled. Mapped to HTTP 403. */
    ACCOUNT_DISABLED,
    UNAUTHENTICATED,
    FORBIDDEN,

    /** The requested mutation conflicts with the resource's current lifecycle state. */
    TOURNAMENT_NOT_EDITABLE,

    /** A team name is already taken within the same tournament. */
    DUPLICATE_TEAM_NAME,

    /** A player name is already taken within the same tournament. */
    DUPLICATE_PLAYER_NAME,

    /** The two players of one team are the same player. */
    DUPLICATE_TEAM_PLAYERS,

    /** The tournament already holds its configured maximum number of teams. */
    TEAM_LIMIT_REACHED,

    /** A tournament edit would drop `max_teams` below the number of teams already registered. */
    MAX_TEAMS_BELOW_TEAM_COUNT,

    /**
     * An admin-management mutation conflicts with the target account's current lifecycle
     * state (approving a non-pending request, reactivating a non-disabled account, ...).
     */
    ADMIN_STATUS_CONFLICT,

    /** An invite token is unknown, or names an invite that is already spent or revoked. */
    INVITE_INVALID,

    /** An invite token is known but its expiry has passed. */
    INVITE_EXPIRED,

    /** An invite token is valid, but the registering email does not match the address it was issued for. */
    INVITE_EMAIL_MISMATCH,

    /** An invite-management mutation conflicts with the invite's current state (revoking a spent/revoked invite). */
    INVITE_STATUS_CONFLICT,

    /**
     * A team access token is unknown, revoked, its team is gone, or its tournament
     * has completed. All four are deliberately indistinguishable.
     */
    TEAM_ACCESS_INVALID,

    /** The organizer already saw the team access link once (or it is revoked); rotation is the recovery. */
    LINK_ALREADY_REVEALED,

    /**
     * The tournament cannot be paired under the current rules — today: an odd team
     * count. Temporary contract until the trio-table slice supports three-team
     * tables; introduced by the Swiss pairing component (#52).
     */
    PAIRING_UNSUPPORTED,

    /** Starting a tournament that is not in the `setup` state (already started or completed). */
    TOURNAMENT_NOT_STARTABLE,

    /** A tournament with fewer than two teams cannot be started. */
    INSUFFICIENT_TEAMS,

    /** A submitted game result violates the scoring rules (#99); field-anchored where sensible. */
    GAME_VALIDATION_INVALID,

    /** A game was created for a natural-key slot that already holds one (#100). */
    GAME_ALREADY_EXISTS,

    /** The game was modified by someone else between read and write (stale version) (#100). */
    GAME_CONCURRENT_MODIFICATION,

    /** The game's round is locked; games of a locked round are read-only (#100, S6). */
    GAME_CONFIRMED,

    /** The addressed game was never created for that natural key (#100). */
    GAME_NOT_FOUND,
}

/**
 * Base class for domain/application failures that map to an HTTP status in the REST
 * layer. The status itself stays out of the vocabulary layer — the web adapter
 * decides it from the concrete exception type.
 */
open class ApiException(
    val code: ErrorCode,
    message: String,
) : RuntimeException(message)

/** Thrown when a requested entity does not exist. Mapped to HTTP 404. */
class NotFoundException(message: String) : ApiException(ErrorCode.NOT_FOUND, message)

/** Thrown when input fails a domain-level validation rule. Mapped to HTTP 400. */
class InvalidInputException(message: String) : ApiException(ErrorCode.INVALID_INPUT, message)

/**
 * Thrown when a specific field of the input fails a validation rule. Carries the
 * offending field so the REST layer can anchor the problem response to it — the
 * frontend shows the message next to that field. Mapped to HTTP 400.
 */
class InvalidFieldException(
    val field: String,
    message: String,
) : ApiException(ErrorCode.INVALID_INPUT, message)

/** Thrown when a registration clashes with an existing account. Mapped to HTTP 409. */
class EmailAlreadyRegisteredException(message: String) :
    ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, message)

/**
 * Thrown when supplied credentials do not match an account. The message deliberately
 * does not reveal whether the email exists. Mapped to HTTP 401.
 */
class InvalidCredentialsException(message: String) :
    ApiException(ErrorCode.INVALID_CREDENTIALS, message)

/**
 * Thrown when credentials are correct but the account is still awaiting approval.
 * Because the password was verified, account existence is already known; the distinct
 * code lets the frontend show "awaiting approval" instead of a generic failure, without
 * leaking anything new. Mapped to HTTP 403.
 */
class AccountPendingException(message: String) :
    ApiException(ErrorCode.ACCOUNT_PENDING, message)

/**
 * Thrown when credentials are correct but the account has been disabled. Mapped to
 * HTTP 403, distinct from [InvalidCredentialsException] so the frontend can say the
 * account was disabled rather than that the password is wrong.
 */
class AccountDisabledException(message: String) :
    ApiException(ErrorCode.ACCOUNT_DISABLED, message)

/** Thrown when a session is missing, invalid or expired. Mapped to HTTP 401. */
class UnauthenticatedException(message: String) :
    ApiException(ErrorCode.UNAUTHENTICATED, message)

/**
 * Thrown when an authenticated admin lacks the role an operation requires. Kept distinct
 * from [UnauthenticatedException] so a signed-in non-owner answers 403 `FORBIDDEN` while
 * an anonymous caller still gets 401 `UNAUTHENTICATED`. Mapped to HTTP 403.
 */
class ForbiddenException(message: String) :
    ApiException(ErrorCode.FORBIDDEN, message)

/**
 * Thrown when an admin-management mutation does not match the target account's current
 * status, e.g. approving an already-active account or disabling one that is not active.
 * Mapped to HTTP 409.
 */
class AdminStatusConflictException(message: String) :
    ApiException(ErrorCode.ADMIN_STATUS_CONFLICT, message)

/**
 * Base for invite rejections. Each concrete subtype carries its own stable [ErrorCode] so
 * the frontend can tell a stale link from a wrong email; the web adapter maps them to
 * statuses.
 */
open class InviteException(code: ErrorCode, message: String) : ApiException(code, message)

/**
 * Thrown when an invite token is unknown, or names an invite that has already been
 * spent or revoked. The three are deliberately indistinguishable so a guessed token
 * reveals nothing about whether it ever existed. Mapped to HTTP 400.
 */
class InviteInvalidException(message: String) : InviteException(ErrorCode.INVITE_INVALID, message)

/** Thrown when a known, usable invite's expiry has passed. Mapped to HTTP 409. */
class InviteExpiredException(message: String) : InviteException(ErrorCode.INVITE_EXPIRED, message)

/**
 * Thrown when a valid invite is presented for a registration with a different email
 * than the address it was issued for. Mapped to HTTP 400.
 */
class InviteEmailMismatchException(message: String) :
    InviteException(ErrorCode.INVITE_EMAIL_MISMATCH, message)

/**
 * Thrown when an owner tries to revoke an invite that is no longer pending. Mapped to
 * HTTP 409.
 */
class InviteStatusConflictException(message: String) :
    InviteException(ErrorCode.INVITE_STATUS_CONFLICT, message)

/**
 * Thrown when a team access link cannot be exchanged. Unknown, revoked and
 * completed-tournament tokens all produce this one exception, so a caller cannot
 * learn whether a guessed token ever existed. Mapped to HTTP 401.
 */
class TeamAccessException(message: String) : ApiException(ErrorCode.TEAM_ACCESS_INVALID, message)

/**
 * Thrown when a team access link cannot be shown: it was already revealed once, or
 * it is revoked and dead as-is. Rotation is the documented recovery in both cases.
 * Mapped to HTTP 409.
 */
class TeamAccessLinkConflictException(message: String) : ApiException(ErrorCode.LINK_ALREADY_REVEALED, message)

/**
 * Thrown when a tournament edit is attempted in a lifecycle state that does not allow
 * it. The tournament status machine is `setup → active → completed`; the configuration
 * is frozen once the tournament has started, so the conflict is a state problem rather
 * than invalid input. Mapped to HTTP 409.
 */
class TournamentNotEditableException(message: String) :
    ApiException(ErrorCode.TOURNAMENT_NOT_EDITABLE, message)

/**
 * Thrown when a tournament that is not in `setup` is started — starting twice or
 * starting a completed tournament. The transition `setup → active` is one-way, so
 * the conflict is a state problem rather than invalid input. Mapped to HTTP 409.
 */
class TournamentNotStartableException(message: String) :
    ApiException(ErrorCode.TOURNAMENT_NOT_STARTABLE, message)

/**
 * Thrown when a tournament with fewer than two teams is started. The threshold is a
 * tournament-level rule, not a field problem, so it is not field-anchored. Mapped
 * to HTTP 409.
 */
class InsufficientTeamsException(message: String) :
    ApiException(ErrorCode.INSUFFICIENT_TEAMS, message)

/**
 * Raised by the Swiss pairing domain component when pairing is impossible under the
 * current rules — today, an odd team count. A temporary contract until the trio-table
 * slice supports three-team tables. Mapped to HTTP 409 by the start-tournament use
 * case (#53).
 */
class PairingUnsupportedException(message: String) :
    ApiException(ErrorCode.PAIRING_UNSUPPORTED, message)

/**
 * Thrown when a team name clashes with an existing team of the same tournament.
 * Field-anchored to the team `name` so the frontend can highlight the input.
 * Mapped to HTTP 409.
 */
class DuplicateTeamNameException(
    val field: String,
    message: String,
) : ApiException(ErrorCode.DUPLICATE_TEAM_NAME, message)

/**
 * Thrown when a player name is already taken within the same tournament (by another
 * team). Field-anchored to the offending player input field. Mapped to HTTP 409.
 */
class DuplicatePlayerNameException(
    val field: String,
    message: String,
) : ApiException(ErrorCode.DUPLICATE_PLAYER_NAME, message)

/**
 * Thrown when the two players of one team are the same player (same name).
 * Field-anchored to the offending player input field. Mapped to HTTP 409.
 */
class DuplicateTeamPlayersException(
    val field: String,
    message: String,
) : ApiException(ErrorCode.DUPLICATE_TEAM_PLAYERS, message)

/**
 * Thrown when a team is created in a tournament that already holds its configured
 * maximum number of teams. The limit is a tournament-level rule, so the failure is
 * not anchored to a team field. Mapped to HTTP 409.
 */
class TeamLimitReachedException(message: String) :
    ApiException(ErrorCode.TEAM_LIMIT_REACHED, message)

/**
 * Thrown when a tournament edit would lower `max_teams` below the number of teams
 * already registered. Field-anchored to `maxTeams` so the frontend highlights that
 * input. Mapped to HTTP 409.
 */
class MaxTeamsBelowTeamCountException(
    val field: String,
    message: String,
) : ApiException(ErrorCode.MAX_TEAMS_BELOW_TEAM_COUNT, message)

/**
 * Thrown by the game scoring domain component (#99) when a submitted game violates
 * the scoring rules. Field-anchored where sensible so the frontend can highlight
 * the offending input. Mapped to HTTP 400 by the scoring API (#100).
 */
class GameValidationException(
    val field: String,
    message: String,
) : ApiException(ErrorCode.GAME_VALIDATION_INVALID, message)

/**
 * Thrown when a game is created for a natural-key slot that already holds one —
 * including the loser of a concurrent create race, resolved by the unique
 * constraint in the schema. Mapped to HTTP 409 by the scoring API (#100).
 */
class GameAlreadyExistsException(message: String) :
    ApiException(ErrorCode.GAME_ALREADY_EXISTS, message)

/**
 * Thrown when a game update carries a stale optimistic-locking version: the game
 * was modified between this writer's read and write, and the first commit wins.
 * Mapped to HTTP 409 by the scoring API (#100).
 */
class GameConcurrentModificationException(message: String) :
    ApiException(ErrorCode.GAME_CONCURRENT_MODIFICATION, message)

/**
 * Thrown when a game is created or edited in a locked round: the round's results
 * were confirmed, so its games are read-only. The confirm/retract workflow is S6
 * (#34); S5 only introduces the round-level lock state. Mapped to HTTP 409.
 */
class GameConfirmedException(message: String) :
    ApiException(ErrorCode.GAME_CONFIRMED, message)

/**
 * Thrown when a game addressed by its natural key does not exist — e.g. a PUT to
 * a game that was never created. Mapped to HTTP 404 by the scoring API (#100).
 */
class GameNotFoundException(message: String) : ApiException(ErrorCode.GAME_NOT_FOUND, message)
