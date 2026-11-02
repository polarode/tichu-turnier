package tichu.infra.rest.web

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GetTeamScoring
import tichu.domain.TournamentId
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Team-session read adapter for scoring (S5c, #101; ADR 0005). The companion to
 * the write-only [GameController]: it exposes the reads the form needs — the
 * team's current table and the games already entered there. Thin: it maps HTTP
 * onto [GetTeamScoring] and passes the authenticated team as the principal, which
 * the team-session filter chain guarantees. The request never names a team.
 */
@RestController
@RequestMapping("/api/team")
class TeamScoringController(
    private val getTeamScoring: GetTeamScoring,
) {

    /** The team's current (in-progress) round table: matchup, roster, confirmation state, settings. */
    @GetMapping("/me/current-table")
    fun currentTable(
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): TeamTableResponseDto = TeamTableResponseDto.from(getTeamScoring.currentTable(principal))

    /** The games entered at a table the team is seated at (absent number = not entered). */
    @GetMapping("/tournaments/{tournamentId}/rounds/{roundNumber}/tables/{tableNumber}/games")
    fun games(
        @PathVariable tournamentId: String,
        @PathVariable roundNumber: Int,
        @PathVariable tableNumber: Int,
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): List<RecordedGameDto> =
        getTeamScoring.games(principal, TournamentId(tournamentId), roundNumber, tableNumber)
            .map(RecordedGameDto::from)
}
