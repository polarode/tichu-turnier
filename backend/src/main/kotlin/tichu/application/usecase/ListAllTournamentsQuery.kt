package tichu.application.usecase

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.ListAllTournaments
import tichu.application.api.OrganizedTournament
import tichu.application.api.OrganizerRole
import tichu.domain.AdminUserRepository
import tichu.domain.TournamentRepository

/**
 * The owner's console list: every tournament on the platform, newest first, each with its
 * creator's email. The owner is lead organizer on all of them, so every row carries
 * [OrganizerRole.LEAD] directly; [TournamentAccess.roleOf] would resolve the same role.
 */
class ListAllTournamentsQuery(
    private val tournamentRepository: TournamentRepository,
    private val adminUserRepository: AdminUserRepository,
) : ListAllTournaments {
    override fun listAll(requester: AuthenticatedAdmin): List<OrganizedTournament> {
        requester.requireOwner()
        return tournamentRepository.findAll()
            .sortedByDescending { it.createdAt }
            .map { organizedTournamentOf(it, OrganizerRole.LEAD, adminUserRepository) }
    }
}
