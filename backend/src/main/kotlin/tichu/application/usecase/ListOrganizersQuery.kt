package tichu.application.usecase

import tichu.application.api.CoOrganizerEntry
import tichu.application.api.LeadOrganizer
import tichu.application.api.ListOrganizers
import tichu.application.api.Organizers
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.CoOrganizerRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId

/**
 * Any organizer of a tournament may list its organizers. A plain class wired as a bean in
 * [UseCaseConfiguration].
 */
class ListOrganizersQuery(
    private val tournamentAccess: TournamentAccess,
    private val coOrganizerRepository: CoOrganizerRepository,
    private val adminUserRepository: AdminUserRepository,
) : ListOrganizers {

    override fun list(tournamentId: TournamentId, caller: AdminUserId): Organizers =
        organizersOf(tournamentAccess.organizedOrThrow(tournamentId, caller), coOrganizerRepository, adminUserRepository)
}

/**
 * The organizer list of [tournament]: its lead (the `ownerId`) and the co-organizers, oldest
 * first. Shared by the list and the add use cases so both return the same shape.
 */
internal fun organizersOf(
    tournament: Tournament,
    coOrganizerRepository: CoOrganizerRepository,
    adminUserRepository: AdminUserRepository,
): Organizers {
    val lead = adminUserRepository.requireById(tournament.ownerId)
    return Organizers(
        lead = LeadOrganizer(adminUserId = lead.id, email = lead.email),
        coOrganizers = coOrganizerRepository.findByTournament(tournament.id).map { row ->
            val admin = adminUserRepository.requireById(row.adminUserId)
            CoOrganizerEntry(adminUserId = admin.id, email = admin.email, addedAt = row.addedAt)
        },
    )
}
