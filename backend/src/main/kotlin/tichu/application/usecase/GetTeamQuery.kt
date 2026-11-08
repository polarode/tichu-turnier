package tichu.application.usecase

import tichu.application.api.GetTeam
import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.NotFoundException

/**
 * Fetches one team of the caller's tournament. Both scoping dimensions are checked:
 * the tournament must be owned by [owner], and the team must belong to that
 * tournament — a valid team id from a foreign tournament fails as not-found, never
 * as forbidden, so the caller cannot learn that the id exists.
 */
class GetTeamQuery(
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
) : GetTeam {

    override fun get(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): Team {
        tournamentAccess.organizedOrThrow(tournamentId, owner)
        return teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
    }
}
