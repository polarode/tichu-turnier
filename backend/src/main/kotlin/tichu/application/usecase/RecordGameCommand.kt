package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GameWriteRequest
import tichu.application.api.RecordGame
import tichu.application.api.RecordedGame
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
import tichu.vocabulary.GameConfirmedException
import tichu.vocabulary.GameNotFoundException
import tichu.vocabulary.TournamentNotActiveException
import org.springframework.transaction.annotation.Transactional

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Authorization: any team seated at the table (its id appears in the table's
 * pairing teams) may create or edit any game at that table — a team session is
 * shared by both team members, so there is no per-team ownership of a game. The
 * seat check happens here, from the session's own team id; the request can never
 * widen it.
 *
 * The whole submit is one transaction (@Transactional): either the game row and
 * its four participants commit together or nothing is written. It takes the
 * round table's lock first and is rejected once the table is frozen (ADR 0006).
 * Games are only written while the tournament is ACTIVE; the status check runs
 * before the freeze check, so a finished tournament answers TOURNAMENT_NOT_ACTIVE.
 * Versioning is optimistic: create assigns version 1, update carries the version
 * and the first commit wins.
 */
@Transactional
class RecordGameCommand(
    private val tournamentRepository: TournamentRepository,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
    private val tournamentEvents: TournamentEvents,
) : RecordGame {

    override fun create(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        principal: AuthenticatedTeam,
    ): RecordedGame = record(tournamentId, roundNumber, tableNumber, request, principal, create = true)

    override fun update(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        principal: AuthenticatedTeam,
    ): RecordedGame = record(tournamentId, roundNumber, tableNumber, request, principal, create = false)

    private fun record(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        principal: AuthenticatedTeam,
        create: Boolean,
    ): RecordedGame {
        requireVersionOnUpdate(create, request)

        val (round, table) = findTable(roundRepository, tournamentId, roundNumber, tableNumber)
        table.requireSeated(principal.teamId)
        requireGameNumber(request.gameNumber, table)

        val tournament = tournamentRepository.findById(tournamentId)
            ?: throw GameNotFoundException("Tournament ${tournamentId.value} not found")
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotActiveException(
                "Tournament ${tournamentId.value} is ${tournament.status}; games are only recorded while it is active",
            )
        }
        requireNotFrozen(TableKey(tournamentId, round.number, table.id.value), table)

        val baseGame = validatedGame(teamRepository, tournament, round, table, request)

        val recorded = if (create) {
            gameRepository.create(baseGame)
        } else {
            updateGame(gameRepository, baseGame.key, request.version!!) {
                // Keep an organizer's correction stamp; only the facts change.
                baseGame.copy(id = it.id, version = it.version, correction = it.correction)
            }
        }

        tournamentEvents.publish(TournamentEvent(TournamentEventType.GAME_RECORDED, tournamentId, round.id, table.id))

        return RecordedGame.of(recorded, tournament.settings)
    }

    /**
     * Takes the table lock (shared with [ConfirmTableCommand]) and rejects the
     * write once any team confirmed: games freeze at the first confirmation
     * (ADR 0006). Under the lock, no confirmation can commit before this write.
     */
    private fun requireNotFrozen(key: TableKey, table: Table) {
        roundRepository.lockTable(key)
        val confirmations = TableConfirmations(table, confirmationRepository.findByTable(key))
        if (confirmations.isFrozen) {
            throw GameConfirmedException(
                "Table ${table.id.value} is ${confirmations.status.name.lowercase()}; its games are frozen",
            )
        }
    }
}
