package tichu.application.usecase

import tichu.application.api.StartNextRound
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.domain.PairingHistory
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.Standings
import tichu.domain.SwissPairing
import tichu.domain.TeamRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.RoundIncompleteException
import tichu.vocabulary.TotalRoundsReachedException
import tichu.vocabulary.TournamentNotActiveException
import org.springframework.transaction.annotation.Transactional
import kotlin.random.Random

/**
 * Starts the next round (S7b, #115). A plain class wired as a bean in
 * [UseCaseConfiguration] — never annotated with @Service.
 *
 * One transaction that first row-locks the tournament, so concurrent advances run
 * one at a time: the second sees the round the first created, still incomplete,
 * and is rejected instead of colliding on the round number. Then the gates: active,
 * current round complete, rounds remaining; the Standings over all
 * completed tables feed [SwissPairing.pairNextRound], with every previous table in
 * the rematch history.
 */
@Transactional
class StartNextRoundCommand(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val roundRepository: RoundRepository,
    private val playedRounds: PlayedRoundsReader,
    private val idGenerator: IdGenerator,
    private val random: Random,
    private val tournamentEvents: TournamentEvents,
) : StartNextRound {

    override fun start(tournamentId: TournamentId, owner: AdminUserId): Round {
        // Lock before reading, so a request that waited sees the state the first one committed.
        tournamentRepository.lock(tournamentId)
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotActiveException(
                "Tournament ${tournamentId.value} is ${tournament.status}; only an active tournament advances",
            )
        }

        val played = playedRounds.of(tournamentId)
        val current = played.last()
        if (!current.round.isComplete(current.confirmations)) {
            throw RoundIncompleteException(
                "Round ${current.round.number} of tournament ${tournamentId.value} still has tables to complete",
            )
        }
        if (played.size >= tournament.settings.totalRounds) {
            throw TotalRoundsReachedException(
                "Tournament ${tournamentId.value} has played all ${tournament.settings.totalRounds} rounds",
            )
        }

        val standings = Standings.compute(teamRepository.findAllByTournament(tournamentId), played, tournament.settings)
        val history = PairingHistory.of(played.flatMap { it.round.pairing.tables })
        val round = Round(
            id = RoundId(idGenerator.newId()),
            tournamentId = tournamentId,
            number = current.round.number + 1,
            pairing = SwissPairing.pairNextRound(standings, history, random),
        )
        val saved = roundRepository.save(round)
        tournamentEvents.publish(TournamentEvent(TournamentEventType.ROUND_STARTED, tournamentId, saved.id))
        return saved
    }
}
