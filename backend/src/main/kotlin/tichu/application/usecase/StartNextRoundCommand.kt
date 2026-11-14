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
import tichu.vocabulary.TooFewActiveTeamsException
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
 * the rematch history. A withdrawn team is not drawn (ADR 0011): only the active
 * teams' standings entries are paired, and the draw needs at least two of them.
 */
@Transactional
class StartNextRoundCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
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
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, owner)
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotActiveException(
                "Tournament ${tournamentId.value} is ${tournament.status}; only an active tournament advances",
            )
        }

        val played = playedRounds.of(tournamentId)
        val current = played.last()
        if (!current.round.isComplete(current.confirmations, current.withdrawals.keys)) {
            throw RoundIncompleteException(
                "Round ${current.round.number} of tournament ${tournamentId.value} still has tables to complete",
            )
        }
        if (played.size >= tournament.settings.totalRounds) {
            throw TotalRoundsReachedException(
                "Tournament ${tournamentId.value} has played all ${tournament.settings.totalRounds} rounds",
            )
        }

        val teams = teamRepository.findAllByTournament(tournamentId)
        val active = teams.filter { !it.isWithdrawn }.map { it.id }.toSet()
        if (active.size < 2) {
            throw TooFewActiveTeamsException(
                "Tournament ${tournamentId.value} has ${active.size} active teams; a round needs at least two",
            )
        }
        val standings = Standings.compute(teams, played, tournament.settings)
        val activeStandings = Standings(standings.entries.filter { it.team.teamId in active })
        val history = PairingHistory.of(played.flatMap { it.round.pairing.tables })
        val round = Round(
            id = RoundId(idGenerator.newId()),
            tournamentId = tournamentId,
            number = current.round.number + 1,
            pairing = SwissPairing.pairNextRound(activeStandings, history, random, tournament.settings.shuffledTables),
        )
        val saved = roundRepository.save(round)
        tournamentEvents.publish(TournamentEvent(TournamentEventType.ROUND_STARTED, tournamentId, saved.id))
        return saved
    }
}
