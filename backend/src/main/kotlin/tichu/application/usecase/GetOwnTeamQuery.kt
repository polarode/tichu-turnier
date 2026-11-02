package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GetOwnTeam
import tichu.application.api.TeamOverview
import tichu.domain.TeamRepository
import tichu.domain.TournamentRepository
import tichu.vocabulary.NotFoundException

/**
 * The use-case-level guard for team sessions: it resolves the principal's own
 * team (plus its tournament's display name) and nothing else. Both scoping
 * dimensions come from the authenticated team session — a team session cannot
 * name a foreign team id, because the query never receives one.
 */
class GetOwnTeamQuery(
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
) : GetOwnTeam {

    override fun get(principal: AuthenticatedTeam): TeamOverview {
        val team = teamRepository.findById(principal.teamId, principal.tournamentId)
            ?: throw NotFoundException("Team ${principal.teamId.value} not found")
        val tournament = tournamentRepository.findById(team.tournamentId)
            ?: throw NotFoundException("Tournament ${team.tournamentId.value} not found")
        return TeamOverview(team = team, tournamentName = tournament.name, tournamentStatus = tournament.status)
    }
}
