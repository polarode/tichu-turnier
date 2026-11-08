package tichu.application.usecase

import tichu.application.api.GetTournament
import tichu.application.api.OrganizedTournament
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.TournamentId

class GetTournamentQuery(
    private val tournamentAccess: TournamentAccess,
    private val adminUserRepository: AdminUserRepository,
) : GetTournament {
    override fun get(id: TournamentId, owner: AdminUserId): OrganizedTournament {
        val (tournament, role) = tournamentAccess.organizedWithRoleOrThrow(id, owner)
        return OrganizedTournament(
            tournament = tournament,
            role = role,
            leadOrganizerEmail = adminUserRepository.requireById(tournament.ownerId).email,
        )
    }
}
