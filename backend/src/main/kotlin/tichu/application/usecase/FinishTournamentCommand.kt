package tichu.application.usecase

import tichu.application.api.FinishTournament
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.RoundIncompleteException
import tichu.vocabulary.RoundsRemainingException
import tichu.vocabulary.TournamentNotActiveException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Finishes the tournament (S7c, #116). A plain class wired as a bean in
 * [UseCaseConfiguration] — never annotated with @Service.
 *
 * One transaction that first row-locks the tournament, so a concurrent advance and
 * finish run one at a time. Then the gates: active, every Total Round played, the
 * last round complete. The count is checked before the last round is read, so a
 * tournament without rounds is rejected as rounds-remaining, not as a crash.
 */
@Transactional
class FinishTournamentCommand(
    private val tournamentRepository: TournamentRepository,
    private val playedRounds: PlayedRoundsReader,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : FinishTournament {

    override fun finish(tournamentId: TournamentId, owner: AdminUserId): Tournament {
        // Lock before reading, so a request that waited sees the state the first one committed.
        tournamentRepository.lock(tournamentId)
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotActiveException(
                "Tournament ${tournamentId.value} is ${tournament.status}; only an active tournament finishes",
            )
        }

        val played = playedRounds.of(tournamentId)
        if (played.size < tournament.settings.totalRounds) {
            throw RoundsRemainingException(
                "Tournament ${tournamentId.value} has played ${played.size} of ${tournament.settings.totalRounds} rounds",
            )
        }
        val last = played.last()
        if (!last.round.isComplete(last.confirmations)) {
            throw RoundIncompleteException(
                "Round ${last.round.number} of tournament ${tournamentId.value} still has tables to complete",
            )
        }

        val finished = tournament.copy(status = TournamentStatus.COMPLETED, updatedAt = clock.instant())
        val saved = tournamentRepository.save(finished)
        tournamentEvents.publish(TournamentEvent(TournamentEventType.TOURNAMENT_FINISHED, tournamentId))
        return saved
    }
}
