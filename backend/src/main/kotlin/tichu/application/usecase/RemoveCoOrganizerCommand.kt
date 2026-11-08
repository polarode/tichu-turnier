package tichu.application.usecase

import tichu.application.api.OrganizerRole
import tichu.application.api.RemoveCoOrganizer
import tichu.domain.AdminUserId
import tichu.domain.CoOrganizerRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.LeadOrganizerCannotLeaveException
import org.springframework.transaction.annotation.Transactional

/**
 * Ends co-organizer access (#132). The lead organizer may remove any co-organizer, and may
 * not leave their own tournament. A co-organizer may only leave, that is target themself.
 * Checks run in this order: the caller organizes the tournament (stranger not-found); the
 * lead targeting themself is refused; a co-organizer targeting anyone else is forbidden.
 * Removing an admin who is not a co-organizer is a silent no-op. A plain class wired as a
 * bean in [UseCaseConfiguration].
 */
@Transactional
class RemoveCoOrganizerCommand(
    private val tournamentAccess: TournamentAccess,
    private val coOrganizerRepository: CoOrganizerRepository,
    private val tournamentEvents: TournamentEvents,
) : RemoveCoOrganizer {

    override fun remove(tournamentId: TournamentId, adminUserId: AdminUserId, caller: AdminUserId) {
        val role = tournamentAccess.organizedWithRoleOrThrow(tournamentId, caller).role
        val leaving = adminUserId == caller

        if (role == OrganizerRole.LEAD && leaving) {
            throw LeadOrganizerCannotLeaveException("The lead organizer cannot leave tournament ${tournamentId.value}")
        }
        if (role == OrganizerRole.CO && !leaving) {
            throw ForbiddenException("Only the lead organizer may remove co-organizers of tournament ${tournamentId.value}")
        }

        coOrganizerRepository.remove(tournamentId, adminUserId)
        tournamentEvents.publish(TournamentEvent(TournamentEventType.ORGANIZERS_CHANGED, tournamentId))
    }
}
