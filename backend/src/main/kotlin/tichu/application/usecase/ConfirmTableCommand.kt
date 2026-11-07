package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.ConfirmTable
import tichu.application.api.SeenGame
import tichu.application.api.TableConfirmationState
import tichu.domain.GameRepository
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableConfirmations
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.GameConcurrentModificationException
import tichu.vocabulary.InvalidFieldException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TableCompletedException
import tichu.vocabulary.TableIncompleteException
import tichu.vocabulary.TournamentNotActiveException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The confirm/retract use case (S6a, #105; ADR 0006). A plain class wired as a
 * bean in [UseCaseConfiguration].
 *
 * Each call is one transaction that first locks the round table, the same lock
 * [RecordGameCommand] takes, so confirm, retract and game writes on one table
 * run one at a time: the games a confirm compares against are the committed
 * ones, and no edit can slip in between that check and the confirmation.
 *
 * Confirm and retract are only accepted while the tournament is ACTIVE
 * ([TournamentNotActiveException]).
 *
 * Confirm checks, in order: seat (403), tournament status, already confirmed (no
 * change), every game entered ([TableIncompleteException]), then the seen versions
 * ([GameConcurrentModificationException] on any difference).
 */
@Transactional
class ConfirmTableCommand(
    private val roundRepository: RoundRepository,
    private val tournamentRepository: TournamentRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
    private val clock: Clock,
) : ConfirmTable {

    override fun confirm(
        key: TableKey,
        seenGames: List<SeenGame>,
        principal: AuthenticatedTeam,
    ): TableConfirmationState {
        val table = lockSeatedTable(key, principal)
        val confirmed = confirmationRepository.findByTable(key)
        if (principal.teamId in confirmed) return state(table, confirmed)
        requireDistinctGameNumbers(seenGames)

        val stored = gameRepository.findByTable(key.tournamentId, key.roundNumber, key.tableNumber)
            .associate { it.gameNumber to it.version }
        if (stored.size < table.gameCount) {
            throw TableIncompleteException(
                "Table ${table.id.value} has ${stored.size} of ${table.gameCount} games entered",
            )
        }
        if (seenGames.associate { it.gameNumber to it.version } != stored) {
            throw GameConcurrentModificationException(
                "The games at table ${table.id.value} changed since they were read; reload and retry",
            )
        }

        confirmationRepository.add(key, principal.teamId, clock.instant())
        return state(table, confirmed + principal.teamId)
    }

    override fun retract(key: TableKey, principal: AuthenticatedTeam): TableConfirmationState {
        val table = lockSeatedTable(key, principal)
        val confirmed = confirmationRepository.findByTable(key)
        if (TableConfirmations(table, confirmed).isCompleted) {
            throw TableCompletedException("Table ${table.id.value} is completed; confirmations are final")
        }
        if (principal.teamId !in confirmed) return state(table, confirmed)

        confirmationRepository.remove(key, principal.teamId)
        return state(table, confirmed - principal.teamId)
    }

    /**
     * Resolves the table, checks the principal is seated there and the tournament is
     * ACTIVE, and takes the table lock.
     */
    private fun lockSeatedTable(key: TableKey, principal: AuthenticatedTeam): Table {
        // The session scopes the request: a foreign tournament is not found, never widened.
        if (key.tournamentId != principal.tournamentId) {
            throw NotFoundException("Tournament ${key.tournamentId.value} not found")
        }
        val table = roundRepository.findByTournamentAndNumber(key.tournamentId, key.roundNumber)
            ?.table(key.tableNumber)
            ?: throw NotFoundException("Table ${key.tableNumber} of round ${key.roundNumber} not found")
        table.requireSeated(principal.teamId)
        val tournament = tournamentRepository.findById(key.tournamentId)
            ?: throw NotFoundException("Tournament ${key.tournamentId.value} not found")
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotActiveException(
                "Tournament ${key.tournamentId.value} is ${tournament.status}; tables are only confirmed while it is active",
            )
        }
        roundRepository.lockTable(key)
        return table
    }

    private fun requireDistinctGameNumbers(seenGames: List<SeenGame>) {
        if (seenGames.map { it.gameNumber }.distinct().size != seenGames.size) {
            throw InvalidFieldException("games", "Each game number may appear only once")
        }
    }

    private fun state(table: Table, confirmed: Set<TeamId>) = TableConfirmationState.of(table, confirmed)
}
