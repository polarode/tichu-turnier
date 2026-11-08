package tichu.application.usecase

import tichu.application.api.ListTournaments
import tichu.application.api.OrganizedTournament
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.CoOrganizerRepository
import tichu.domain.TournamentRepository

/**
 * The dashboard list: the tournaments the admin leads plus those they co-organize, one
 * list, newest first. Each row carries the caller's role and the lead organizer's email.
 */
class ListTournamentsQuery(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val coOrganizerRepository: CoOrganizerRepository,
    private val adminUserRepository: AdminUserRepository,
) : ListTournaments {
    override fun list(owner: AdminUserId): List<OrganizedTournament> {
        val led = tournamentRepository.findAllByOwner(owner)
        val ledIds = led.map { it.id }.toSet()
        val coOrganized = coOrganizerRepository.findTournamentIdsByAdmin(owner)
            .filter { it !in ledIds }
            .mapNotNull { tournamentRepository.findById(it) }
        return (led + coOrganized)
            .sortedByDescending { it.createdAt }
            .mapNotNull { tournament ->
                val role = tournamentAccess.roleOf(tournament, owner) ?: return@mapNotNull null
                OrganizedTournament(
                    tournament = tournament,
                    role = role,
                    leadOrganizerEmail = adminUserRepository.requireById(tournament.ownerId).email,
                )
            }
    }
}
