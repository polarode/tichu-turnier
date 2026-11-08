package tichu.application.usecase

import tichu.application.api.SetPublicVisibility
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Switches the public visibility of a tournament. Unlike the Total Rounds, it has no
 * status gate: it can be changed in setup, active, completed and published alike. A
 * plain class wired as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Setting the value the tournament already has is a no-op: the tournament is returned
 * unchanged, without a save and without a live event.
 */
@Transactional
class SetPublicVisibilityCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : SetPublicVisibility {

    override fun set(id: TournamentId, publicVisibility: Boolean, owner: AdminUserId): Tournament {
        val tournament = tournamentAccess.organizedOrThrow(id, owner)
        if (publicVisibility == tournament.publicVisibility) return tournament

        val saved = tournamentRepository.save(
            tournament.copy(publicVisibility = publicVisibility, updatedAt = clock.instant()),
        )
        tournamentEvents.publish(TournamentEvent(TournamentEventType.PUBLIC_VISIBILITY_CHANGED, id))
        return saved
    }
}
