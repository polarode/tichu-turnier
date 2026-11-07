package tichu.application.usecase

import tichu.application.api.PublishTournament
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.TournamentNotCompletedException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Publishes the results of a completed tournament (S7c, #116). A plain class wired as a
 * bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * One transaction that first row-locks the tournament, so a concurrent publish runs
 * one at a time and the second one sees `published` and is rejected.
 */
@Transactional
class PublishTournamentCommand(
    private val tournamentRepository: TournamentRepository,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : PublishTournament {

    override fun publish(tournamentId: TournamentId, owner: AdminUserId): Tournament {
        // Lock before reading, so a request that waited sees the state the first one committed.
        tournamentRepository.lock(tournamentId)
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        if (tournament.status != TournamentStatus.COMPLETED) {
            throw TournamentNotCompletedException(
                "Tournament ${tournamentId.value} is ${tournament.status}; only a completed tournament publishes",
            )
        }

        val published = tournament.copy(status = TournamentStatus.PUBLISHED, updatedAt = clock.instant())
        val saved = tournamentRepository.save(published)
        tournamentEvents.publish(TournamentEvent(TournamentEventType.RESULTS_PUBLISHED, tournamentId))
        return saved
    }
}
