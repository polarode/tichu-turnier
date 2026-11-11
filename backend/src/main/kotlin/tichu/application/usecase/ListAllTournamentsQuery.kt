package tichu.application.usecase

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.ListAllTournaments
import tichu.application.api.OrganizedTournament
import tichu.application.api.OrganizerRole
import tichu.domain.AdminUserRepository
import tichu.domain.TournamentRepository

/**
 * The owner's console list: every tournament on the platform, newest first, each with its
 * lead organizer's email. Every row carries [OrganizerRole.LEAD], the role the owner holds
 * on every tournament (see [TournamentAccess.roleOf]).
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
