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
 * Thrown when a tournament edit is attempted in a lifecycle state that does not allow
 * it. The tournament status machine is `setup → active → completed`; the configuration
 * is frozen once the tournament has started, so the conflict is a state problem rather
 * than invalid input. Mapped to HTTP 409.
 */
class TournamentNotEditableException(message: String) :
    ApiException(ErrorCode.TOURNAMENT_NOT_EDITABLE, message)

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
