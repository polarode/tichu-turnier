package com.example.template.application.usecase

import com.example.template.application.api.StartTournament
import com.example.template.application.api.StartedTournament
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.SwissPairing
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentStatus
import com.example.template.vocabulary.InsufficientTeamsException
import com.example.template.vocabulary.TournamentNotStartableException
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

        // Odd team counts surface as PairingUnsupportedException from the pairing
        // component — the temporary contract until the trio-table slice lands.
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
        return StartedTournament(started, round)
    }
}
