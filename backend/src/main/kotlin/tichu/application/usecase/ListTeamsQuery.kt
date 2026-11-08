package tichu.application.usecase

import tichu.application.api.ListTeams
import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository

/** Lists the teams of the caller's tournament. Ownership-scoped: 404 on foreign ids. */
class ListTeamsQuery(
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
) : ListTeams {

    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<Team> {
        tournamentAccess.organizedOrThrow(tournamentId, owner)
        return teamRepository.findAllByTournament(tournamentId)
    }
}
