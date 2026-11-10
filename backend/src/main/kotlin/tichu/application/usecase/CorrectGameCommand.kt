package tichu.application.usecase

import tichu.application.api.CorrectGame
import tichu.application.api.GameWriteRequest
import tichu.application.api.RecordedGame
import tichu.domain.AdminUserId
import tichu.domain.Game
import tichu.domain.GameCorrection
import tichu.domain.GameKey
import tichu.domain.GameRepository
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableConfirmations
import tichu.domain.TableKey
import tichu.domain.TeamRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.GameConcurrentModificationException
import tichu.vocabulary.GameNotFoundException
import tichu.vocabulary.TableNotCompletedException
import tichu.vocabulary.TournamentNotActiveException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The organizer's game correction (last-resort fix, ADR 0010). A plain
 * class wired as a bean in [UseCaseConfiguration].
 *
 * One transaction. The tournament is row-locked first, so a correction and a
 * concurrent finish run one at a time, then the organizer check runs
 * ([TournamentAccess]: the lead or a co-organizer, otherwise not-found).
 *
 * The raw facts are validated exactly as a team edit validates them
 * ([GameWriteRules]), the outcome is re-derived from them, and the version is
 * checked by the conditional update: the first commit wins. The correction stamps
 * the organizer and the instant from the injected [Clock]. Standings are computed
 * on read, so the correction reaches them without further writes.
 */
@Transactional
class CorrectGameCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : CorrectGame {

    override fun correct(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        admin: AdminUserId,
    ): RecordedGame {
        // Lock before reading, so a request that waited sees the state the first one committed.
        tournamentRepository.lock(tournamentId)
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, admin)
        if (tournament.status != TournamentStatus.ACTIVE && tournament.status != TournamentStatus.COMPLETED) {
            throw TournamentNotActiveException(
                "Tournament ${tournamentId.value} is ${tournament.status}; games are only corrected while it is active or completed",
            )
        }

        requireVersionOnUpdate(create = false, request)
        val round = roundRepository.findByTournamentAndNumber(tournamentId, roundNumber)
            ?: throw GameNotFoundException("Round $roundNumber of tournament ${tournamentId.value} not found")
        val table = round.table(tableNumber)
            ?: throw GameNotFoundException("Table $tableNumber of round ${round.number} not found")
        requireGameNumber(request.gameNumber, table)
        requireCompleted(TableKey(tournamentId, roundNumber, tableNumber), table)

        val teamResults = toTeamResults(request)
        requirePlayingTeams(teamRepository, tournamentId, teamResults, table.playingTeams(request.gameNumber))

        val key = GameKey(tournamentId, round.number, table.id.value, request.gameNumber)
        val baseGame = Game.record(key, teamResults, request.beschiss, tournament.settings)
        // Existence first, so a missing game answers 404 — the stale-version race answers 409.
        gameRepository.find(key)
            ?: throw GameNotFoundException(
                "Game ${request.gameNumber} at table ${table.id.value} of round ${round.number} not found",
            )
        val corrected = gameRepository.updateWithVersion(key, request.version!!) {
            baseGame.copy(
                id = it.id,
                version = it.version,
                correction = GameCorrection(admin, clock.instant()),
            )
        }
            ?: throw GameConcurrentModificationException(
                "Game ${request.gameNumber} was modified concurrently; reload and retry",
            )

        tournamentEvents.publish(TournamentEvent(TournamentEventType.GAME_RECORDED, tournamentId, round.id, table.id))

        return RecordedGame.of(corrected, tournament.settings)
    }

    /**
     * Takes the table lock (shared with the team writes and confirmations) and
     * requires the table COMPLETED: its games are final only then (ADR 0006). Under
     * the lock, no confirmation or retract can change the status mid-correction.
     */
    private fun requireCompleted(key: TableKey, table: Table) {
        roundRepository.lockTable(key)
        if (!TableConfirmations(table, confirmationRepository.findByTable(key)).isCompleted) {
            throw TableNotCompletedException(
                "Table ${table.id.value} is not completed; only a completed table's games can be corrected",
            )
        }
    }
}
