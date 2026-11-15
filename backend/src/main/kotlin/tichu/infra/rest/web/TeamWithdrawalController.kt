package tichu.infra.rest.web

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.UndoWithdrawal
import tichu.application.api.WithdrawTeam
import tichu.domain.TeamId
import tichu.domain.TournamentId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for the organizer's Withdrawal of a team (ADR 0011). It sits beside the
 * team controller because a withdrawal is a team-level organizer write. The principal is the
 * admin session; the organizer check happens in the use cases, so a stranger gets 404.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/teams/{teamId}/withdrawal")
class TeamWithdrawalController(
    private val withdrawTeam: WithdrawTeam,
    private val undoWithdrawal: UndoWithdrawal,
) {

    /** Withdraws the team; answers the stored withdrawal. */
    @PostMapping
    fun withdraw(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @Valid @RequestBody body: TeamWithdrawalRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): WithdrawalResponseDto = WithdrawalResponseDto.from(
        withdrawTeam.withdraw(
            TournamentId(tournamentId),
            TeamId(teamId),
            body.reason!!,
            body.compensation!!,
            principal.adminUserId,
        ),
    )

    /** Undoes the team's withdrawal, removing its Forfeit Games where it had produced them. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun undo(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ) {
        undoWithdrawal.undo(TournamentId(tournamentId), TeamId(teamId), principal.adminUserId)
    }
}
