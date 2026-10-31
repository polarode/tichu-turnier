package com.example.template.infra.rest.web

import com.example.template.vocabulary.AdminStatusConflictException
import com.example.template.vocabulary.ApiException
import com.example.template.vocabulary.AccountDisabledException
import com.example.template.vocabulary.AccountPendingException
import com.example.template.vocabulary.DuplicatePlayerNameException
import com.example.template.vocabulary.DuplicateTeamNameException
import com.example.template.vocabulary.DuplicateTeamPlayersException
import com.example.template.vocabulary.EmailAlreadyRegisteredException
import com.example.template.vocabulary.ErrorCode
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.GameAlreadyExistsException
import com.example.template.vocabulary.GameConfirmedException
import com.example.template.vocabulary.GameConcurrentModificationException
import com.example.template.vocabulary.GameNotFoundException
import com.example.template.vocabulary.GameValidationException
import com.example.template.vocabulary.InvalidCredentialsException
import com.example.template.vocabulary.InvalidFieldException
import com.example.template.vocabulary.InvalidInputException
import com.example.template.vocabulary.InviteEmailMismatchException
import com.example.template.vocabulary.InviteExpiredException
import com.example.template.vocabulary.InviteInvalidException
import com.example.template.vocabulary.InviteStatusConflictException
import com.example.template.vocabulary.InsufficientTeamsException
import com.example.template.vocabulary.MaxTeamsBelowTeamCountException
import com.example.template.vocabulary.NotFoundException
import com.example.template.vocabulary.PairingUnsupportedException
import com.example.template.vocabulary.TeamAccessException
import com.example.template.vocabulary.TeamAccessLinkConflictException
import com.example.template.vocabulary.TeamLimitReachedException
import com.example.template.vocabulary.TournamentNotEditableException
import com.example.template.vocabulary.TournamentNotStartableException
import com.example.template.vocabulary.UnauthenticatedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import tools.jackson.databind.exc.InvalidNullException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Translates domain/application exceptions into RFC 7807 problem responses, so the
 * HTTP status is decided in one place and the use cases stay transport-agnostic. Every
 * response carries a stable `code` member the frontend localizes against.
 */
@RestControllerAdvice
class WebControllerAdvice {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex)

    /** No route matched: JSON ProblemDetail, never HTML. */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "No resource at this path.")

    @ExceptionHandler(InvalidInputException::class)
    fun handleInvalidInput(ex: InvalidInputException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex)

    /** Field-anchored validation failure: the offending field travels as a member. */
    @ExceptionHandler(InvalidFieldException::class)
    fun handleInvalidField(ex: InvalidFieldException): ProblemDetail {
        val problem = problem(HttpStatus.BAD_REQUEST, ex)
        problem.setProperty("field", ex.field)
        return problem
    }

    @ExceptionHandler(EmailAlreadyRegisteredException::class)
    fun handleEmailAlreadyRegistered(ex: EmailAlreadyRegisteredException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, ex)

    /**
     * Correct credentials on a pending account: 403, not 401, so the frontend can show
     * "awaiting approval" without muddling it with a wrong password.
     */
    @ExceptionHandler(AccountPendingException::class)
    fun handleAccountPending(ex: AccountPendingException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, ex)

    /** Correct credentials on a disabled account: 403, distinguishable from a bad password. */
    @ExceptionHandler(AccountDisabledException::class)
    fun handleAccountDisabled(ex: AccountDisabledException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, ex)

    @ExceptionHandler(UnauthenticatedException::class)
    fun handleUnauthenticated(ex: UnauthenticatedException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, ex)

    /**
     * Authenticated but not authorized: a signed-in non-owner hitting an owner-only
     * use case. 403, kept distinct from the 401 the security chain returns when no
     * valid session is present at all.
     */
    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, ex)

    /** An admin-management mutation that contradicts the target's current status: 409. */
    @ExceptionHandler(AdminStatusConflictException::class)
    fun handleAdminStatusConflict(ex: AdminStatusConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** An unknown, spent or revoked invite presented at registration: 400. */
    @ExceptionHandler(InviteInvalidException::class)
    fun handleInviteInvalid(ex: InviteInvalidException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex)

    /** A valid invite used with a different email than it was issued for: 400. */
    @ExceptionHandler(InviteEmailMismatchException::class)
    fun handleInviteEmailMismatch(ex: InviteEmailMismatchException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, ex)

    /** A known invite whose lifetime has passed: 409, a state conflict rather than bad input. */
    @ExceptionHandler(InviteExpiredException::class)
    fun handleInviteExpired(ex: InviteExpiredException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** Revoking an invite that is no longer pending: 409. */
    @ExceptionHandler(InviteStatusConflictException::class)
    fun handleInviteStatusConflict(ex: InviteStatusConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** An unknown, revoked or completed-tournament team access token: 401, no existence disclosure. */
    @ExceptionHandler(TeamAccessException::class)
    fun handleTeamAccess(ex: TeamAccessException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, ex)

    /** The team access link was already revealed once or is revoked: 409; rotation is the recovery. */
    @ExceptionHandler(TeamAccessLinkConflictException::class)
    fun handleTeamAccessLinkConflict(ex: TeamAccessLinkConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** The resource exists and is owned, but its state forbids the mutation: HTTP 409. */
    @ExceptionHandler(TournamentNotEditableException::class)
    fun handleTournamentNotEditable(ex: TournamentNotEditableException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** Starting is blocked by the tournament's lifecycle state (started twice, completed): HTTP 409. */
    @ExceptionHandler(TournamentNotStartableException::class)
    fun handleTournamentNotStartable(ex: TournamentNotStartableException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** The tournament cannot start because it holds fewer than two teams: HTTP 409. */
    @ExceptionHandler(InsufficientTeamsException::class)
    fun handleInsufficientTeams(ex: InsufficientTeamsException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** The tournament cannot be paired under the current rules (odd team count): HTTP 409. */
    @ExceptionHandler(PairingUnsupportedException::class)
    fun handlePairingUnsupported(ex: PairingUnsupportedException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** Uniqueness violations of teams/players: 409, field-anchored like InvalidFieldException. */
    @ExceptionHandler(DuplicateTeamNameException::class)
    fun handleDuplicateTeamName(ex: DuplicateTeamNameException): ProblemDetail =
        anchoredConflict(ex, ex.field)

    @ExceptionHandler(DuplicatePlayerNameException::class)
    fun handleDuplicatePlayerName(ex: DuplicatePlayerNameException): ProblemDetail =
        anchoredConflict(ex, ex.field)

    @ExceptionHandler(DuplicateTeamPlayersException::class)
    fun handleDuplicateTeamPlayers(ex: DuplicateTeamPlayersException): ProblemDetail =
        anchoredConflict(ex, ex.field)

    /** The tournament already holds its configured maximum number of teams: HTTP 409. */
    @ExceptionHandler(TeamLimitReachedException::class)
    fun handleTeamLimitReached(ex: TeamLimitReachedException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** Lowering `max_teams` below the registered team count: 409, anchored to `maxTeams`. */
    @ExceptionHandler(MaxTeamsBelowTeamCountException::class)
    fun handleMaxTeamsBelowTeamCount(ex: MaxTeamsBelowTeamCountException): ProblemDetail =
        anchoredConflict(ex, ex.field)

    /** A submitted game violates the scoring rules: 400, field-anchored where sensible (#100). */
    @ExceptionHandler(GameValidationException::class)
    fun handleGameValidation(ex: GameValidationException): ProblemDetail =
        anchoredBadRequest(ex, ex.field)

    /** The natural-key slot already holds a game (incl. a lost create race): 409. */
    @ExceptionHandler(GameAlreadyExistsException::class)
    fun handleGameAlreadyExists(ex: GameAlreadyExistsException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** Stale optimistic-locking version — first commit wins: 409. */
    @ExceptionHandler(GameConcurrentModificationException::class)
    fun handleGameConcurrentModification(ex: GameConcurrentModificationException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** The game's round is locked; games of a locked round are read-only: 409. */
    @ExceptionHandler(GameConfirmedException::class)
    fun handleGameConfirmed(ex: GameConfirmedException): ProblemDetail =
        problem(HttpStatus.CONFLICT, ex)

    /** The addressed game (or its round/table) does not exist: 404. */
    @ExceptionHandler(GameNotFoundException::class)
    fun handleGameNotFound(ex: GameNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, ex)

    private fun anchoredConflict(ex: ApiException, field: String): ProblemDetail {
        val problem = problem(HttpStatus.CONFLICT, ex)
        problem.anchor(field)
        return problem
    }

    /** 400 variant of the field-anchored funnel, for validation-type failures. */
    private fun anchoredBadRequest(ex: ApiException, field: String): ProblemDetail {
        val problem = problem(HttpStatus.BAD_REQUEST, ex)
        problem.anchor(field)
        return problem
    }

    /** Malformed/missing JSON body: same 400 shape, field-anchored when the
     *  deserializer names the offending property (e.g. a missing required member).
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ProblemDetail {
        val problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, "Request body is not readable.")
        var cause: Throwable? = ex.cause
        while (cause != null) {
            if (cause is InvalidNullException) {
                cause.propertyName?.simpleName?.takeIf { it.isNotEmpty() }?.let { problem.anchor(it) }
                break
            }
            // Jackson reports a missing required property without a structured
            // property name, only in the message: "Missing required creator
            // property 'maxTeams' (index 1)".
            missingCreatorProperty.find(cause.message ?: "")?.groupValues?.get(1)?.let {
                problem.anchor(it)
                break
            }
            cause = cause.cause
        }
        return problem
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val fieldErrors = ex.bindingResult.fieldErrors
        val message = fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Validation failed" }
        val problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT, message)
        // Bean-validation failures are field-anchored too, same as InvalidFieldException.
        fieldErrors.firstOrNull()?.field?.let { problem.anchor(it) }
        return problem
    }

    private companion object {
        private val missingCreatorProperty = Regex("Missing required creator property '(\\w+)'")
    }

    /** Every field-anchored response funnels through here. */
    private fun ProblemDetail.anchor(field: String) {
        setProperty("field", field)
    }

    private fun problem(status: HttpStatus, ex: ApiException): ProblemDetail =
        problem(status, ex.code, ex.message ?: status.reasonPhrase)

    private fun problem(status: HttpStatus, code: ErrorCode, message: String): ProblemDetail =
        problemDetail(status, code, message)
}
