package tichu.application.usecase

import tichu.application.api.ListTournaments
import tichu.application.api.OrganizedTournament
import tichu.application.api.OrganizerRole
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.CoOrganizerRepository
import tichu.domain.TournamentRepository

/**
 * The dashboard list: the tournaments the admin leads plus those they co-organize, one
 * list, newest first. Each row carries the caller's role and the lead organizer's email.
 * The role is known from which lookup produced the row, so no per-row role check runs.
 */
class ListTournamentsQuery(
    private val tournamentRepository: TournamentRepository,
    private val coOrganizerRepository: CoOrganizerRepository,
    private val adminUserRepository: AdminUserRepository,
) : ListTournaments {
    override fun list(caller: AdminUserId): List<OrganizedTournament> {
        val led = tournamentRepository.findAllByOwner(caller).map { RoleOnTournament(it, OrganizerRole.LEAD) }
        val ledIds = led.map { it.tournament.id }.toSet()
        val coOrganized = coOrganizerRepository.findTournamentIdsByAdmin(caller)
            .filter { it !in ledIds }
            .mapNotNull { tournamentRepository.findById(it) }
            .map { RoleOnTournament(it, OrganizerRole.CO) }
        return (led + coOrganized)
            .sortedByDescending { it.tournament.createdAt }
            .map { organizedTournamentOf(it.tournament, it.role, adminUserRepository) }
    }
}
