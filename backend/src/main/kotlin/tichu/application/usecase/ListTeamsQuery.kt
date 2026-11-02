package tichu.application.usecase

import tichu.application.api.ListTeams
import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository

/** Lists the teams of the caller's tournament. Ownership-scoped: 404 on foreign ids. */
class ListTeamsQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) : ListTeams {

    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<Team> {
        tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        return teamRepository.findAllByTournament(tournamentId)
    }
}
