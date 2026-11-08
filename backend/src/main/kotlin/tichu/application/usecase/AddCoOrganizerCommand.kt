package tichu.application.usecase

import tichu.application.api.AddCoOrganizer
import tichu.application.api.Organizers
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.CoOrganizer
import tichu.domain.CoOrganizerRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.normalizeEmail
import tichu.vocabulary.AlreadyCoOrganizerException
import tichu.vocabulary.CannotAddSelfAsCoOrganizerException
import tichu.vocabulary.NoActiveAdminWithEmailException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Lead-only: adds an active admin as co-organizer of a tournament, in any status. Checks run
 * in this order: the caller is the lead (stranger not-found, co-organizer forbidden); the
 * email names an active admin (unknown, pending and disabled share one refusal); the admin is
 * not the lead; the admin is not already a co-organizer. A plain class wired as a bean in
 * [UseCaseConfiguration].
 */
@Transactional
class AddCoOrganizerCommand(
    private val tournamentAccess: TournamentAccess,
    private val coOrganizerRepository: CoOrganizerRepository,
    private val adminUserRepository: AdminUserRepository,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : AddCoOrganizer {

    override fun add(tournamentId: TournamentId, email: String, caller: AdminUserId): Organizers {
        val tournament = tournamentAccess.ledOrThrow(tournamentId, caller)

        val admin = adminUserRepository.findByEmail(normalizeEmail(email))
        if (admin == null || !admin.isActive()) {
            throw NoActiveAdminWithEmailException(EMAIL_FIELD, "No active admin account has this email")
        }
        if (tournament.isOwnedBy(admin.id)) {
            throw CannotAddSelfAsCoOrganizerException(EMAIL_FIELD, "The lead organizer cannot be added as co-organizer")
        }
        if (coOrganizerRepository.isCoOrganizer(tournamentId, admin.id)) {
            throw AlreadyCoOrganizerException(EMAIL_FIELD, "This admin already co-organizes the tournament")
        }

        coOrganizerRepository.add(CoOrganizer(tournamentId, admin.id, clock.instant()))
        tournamentEvents.publish(TournamentEvent(TournamentEventType.ORGANIZERS_CHANGED, tournamentId))
        return organizersOf(tournament, coOrganizerRepository, adminUserRepository)
    }

    private companion object {
        const val EMAIL_FIELD = "email"
    }
}
