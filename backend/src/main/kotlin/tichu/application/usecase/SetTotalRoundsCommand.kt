package tichu.application.usecase

import tichu.application.api.SetTotalRounds
import tichu.domain.AdminUserId
import tichu.domain.RoundRepository
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.TotalRoundsBelowRoundCountException
import tichu.vocabulary.TournamentNotEditableException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Changes only the Total Rounds of an active tournament (GLOSSARY.md); in setup they
 * are part of the tournament form. A plain class wired as a bean in
 * [UseCaseConfiguration] — never annotated with @Service.
 *
 * Checks in the order of [UpdateTournamentCommand]: ownership (404), the status
 * machine (409 unless active), the field range (400), then the round count (409,
 * never below the rounds already drawn).
 *
 * Setting the Total Rounds to the value they already have is a no-op: the tournament
 * is returned unchanged, without a save and without a live event.
 */
@Transactional
class SetTotalRoundsCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val roundRepository: RoundRepository,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : SetTotalRounds {

    override fun set(id: TournamentId, totalRounds: Int, owner: AdminUserId): Tournament {
        val tournament = tournamentAccess.organizedOrThrow(id, owner)
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotEditableException(
                "Tournament ${id.value}'s total rounds change here only while active; " +
                    "in setup through the tournament form (current status: ${tournament.status})",
            )
        }
        TournamentInputValidator.validateTotalRounds(totalRounds)

        val roundCount = roundRepository.findByTournament(id).size
        if (totalRounds < roundCount) {
            throw TotalRoundsBelowRoundCountException(
                "totalRounds",
                "Tournament already has $roundCount rounds drawn; total rounds cannot be lowered below that",
            )
        }

        if (totalRounds == tournament.settings.totalRounds) return tournament

        val saved = tournamentRepository.save(
            tournament.copy(
                settings = tournament.settings.copy(totalRounds = totalRounds),
                updatedAt = clock.instant(),
            ),
        )
        tournamentEvents.publish(TournamentEvent(TournamentEventType.TOTAL_ROUNDS_CHANGED, id))
        return saved
    }
}
