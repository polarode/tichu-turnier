package tichu.application.usecase

import tichu.application.api.StartTournament
import tichu.application.api.StartedTournament
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.SwissPairing
import tichu.domain.TeamRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.InsufficientTeamsException
import tichu.vocabulary.TournamentNotStartableException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import kotlin.random.Random

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * The whole start flow is one transaction: the status transition and the round
 * creation commit together or not at all — a failed start leaves no partial round.
 */
@Transactional
class StartTournamentCommand(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val roundRepository: RoundRepository,
    private val idGenerator: IdGenerator,
    private val random: Random,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : StartTournament {

    override fun start(tournamentId: TournamentId, owner: AdminUserId): StartedTournament {
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        if (tournament.status != TournamentStatus.SETUP) {
            throw TournamentNotStartableException(
                "Tournament ${tournamentId.value} cannot be started: it is ${tournament.status}, not SETUP.",
            )
        }
        val teams = teamRepository.findAllByTournament(tournamentId)
        if (teams.size < 2) {
            throw InsufficientTeamsException(
                "Tournament ${tournamentId.value} needs at least 2 teams to start, has ${teams.size}.",
            )
        }

        // An odd count seats the last three shuffled teams at a trio table.
        val pairing = SwissPairing.pairFirstRound(teams.map { it.id }, random)
        val round = Round(
            id = RoundId(idGenerator.newId()),
            tournamentId = tournamentId,
            number = 1,
            pairing = pairing,
        )
        roundRepository.save(round)

        val started = tournament.copy(status = TournamentStatus.ACTIVE, updatedAt = clock.instant())
        tournamentRepository.save(started)
        tournamentEvents.publish(TournamentEvent(TournamentEventType.TOURNAMENT_STARTED, tournamentId, round.id))
        return StartedTournament(started, round)
    }
}
