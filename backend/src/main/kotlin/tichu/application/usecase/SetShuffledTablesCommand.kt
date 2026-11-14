package tichu.application.usecase

import tichu.application.api.SetShuffledTables
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.TournamentNotEditableException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Switches Shuffled Tables of a tournament that is still in setup or active; once it is
 * completed or published the seating is settled. A plain class wired as a bean in
 * [UseCaseConfiguration] — never annotated with @Service.
 *
 * Checks in the order of [SetTotalRoundsCommand]: ownership (404), then the status gate
 * (409 unless setup or active).
 *
 * Setting the value the tournament already has is a no-op: the tournament is returned
 * unchanged, without a save and without a live event.
 */
@Transactional
class SetShuffledTablesCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : SetShuffledTables {

    override fun set(id: TournamentId, shuffledTables: Boolean, owner: AdminUserId): Tournament {
        val tournament = tournamentAccess.organizedOrThrow(id, owner)
        if (tournament.status != TournamentStatus.SETUP && tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotEditableException(
                "Tournament ${id.value}'s shuffled tables cannot change once it is finished " +
                    "(current status: ${tournament.status})",
            )
        }

        if (shuffledTables == tournament.settings.shuffledTables) return tournament

        val saved = tournamentRepository.save(
            tournament.copy(
                settings = tournament.settings.copy(shuffledTables = shuffledTables),
                updatedAt = clock.instant(),
            ),
        )
        tournamentEvents.publish(TournamentEvent(TournamentEventType.SHUFFLED_TABLES_CHANGED, id))
        return saved
    }
}
