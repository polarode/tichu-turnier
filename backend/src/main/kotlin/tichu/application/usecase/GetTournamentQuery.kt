package tichu.application.usecase

import tichu.application.api.GetTournament
import tichu.application.api.OrganizedTournament
import tichu.application.api.OrganizerRole
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId

class GetTournamentQuery(
    private val tournamentAccess: TournamentAccess,
    private val adminUserRepository: AdminUserRepository,
) : GetTournament {
    override fun get(id: TournamentId, caller: AdminUserId): OrganizedTournament {
        val organized = tournamentAccess.organizedWithRoleOrThrow(id, caller)
        return organizedTournamentOf(organized.tournament, organized.role, adminUserRepository)
    }
}

/**
 * [tournament] as seen by an organizer with [role]: the lead organizer's email comes along
 * with it. Shared by the single read and the dashboard list so both return the same shape.
 */
internal fun organizedTournamentOf(
    tournament: Tournament,
    role: OrganizerRole,
    adminUserRepository: AdminUserRepository,
): OrganizedTournament = OrganizedTournament(
    tournament = tournament,
    role = role,
    leadOrganizerEmail = adminUserRepository.requireById(tournament.ownerId).email,
)
