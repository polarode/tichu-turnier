package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.ConfirmTable
import tichu.application.api.SeenGame
import tichu.application.api.TableConfirmationState
import tichu.domain.GameRepository
import tichu.domain.Round
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableConfirmations
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.domain.Withdrawal
import tichu.vocabulary.GameConcurrentModificationException
import tichu.vocabulary.InvalidFieldException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TableCompletedException
import tichu.vocabulary.TableIncompleteException
import tichu.vocabulary.TeamWithdrawnException
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
 * Confirm checks, in order: seat (403), tournament status, withdrawn session
 * ([TeamWithdrawnException], ADR 0011), already confirmed (no change), every game
 * entered ([TableIncompleteException]), then the seen versions
 * ([GameConcurrentModificationException] on any difference). A Forfeit Game counts as
 * entered; a withdrawn team's confirmation is not needed for completion.
 */
@Transactional
class ConfirmTableCommand(
    private val roundRepository: RoundRepository,
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : ConfirmTable {

    override fun confirm(
        key: TableKey,
        seenGames: List<SeenGame>,
        principal: AuthenticatedTeam,
    ): TableConfirmationState {
        val (round, table, withdrawals) = lockSeatedTable(key, principal)
        val confirmed = confirmationRepository.findByTable(key)
        if (principal.teamId in confirmed) return state(table, confirmed, withdrawals.keys)
        requireDistinctGameNumbers(seenGames)

        val stored = gameRepository.findByTable(key.tournamentId, key.roundNumber, key.tableNumber)
        val forfeits = tableForfeits(round, table, stored, withdrawals)
        val covered = stored.size + forfeits.size
        if (covered < table.gameCount) {
            throw TableIncompleteException(
                "Table ${table.id.value} has $covered of ${table.gameCount} games entered",
            )
        }
        if (seenGames.associate { it.gameNumber to it.version } != stored.associate { it.gameNumber to it.version }) {
            throw GameConcurrentModificationException(
                "The games at table ${table.id.value} changed since they were read; reload and retry",
            )
        }

        confirmationRepository.add(key, principal.teamId, clock.instant())
        publish(TournamentEventType.TABLE_CONFIRMED, key, round, table)
        return state(table, confirmed + principal.teamId, withdrawals.keys)
    }

    override fun retract(key: TableKey, principal: AuthenticatedTeam): TableConfirmationState {
        val (round, table, withdrawals) = lockSeatedTable(key, principal)
        val confirmed = confirmationRepository.findByTable(key)
        if (TableConfirmations(table, confirmed, withdrawals.keys).isCompleted) {
            throw TableCompletedException("Table ${table.id.value} is completed; confirmations are final")
        }
        if (principal.teamId !in confirmed) return state(table, confirmed, withdrawals.keys)

        confirmationRepository.remove(key, principal.teamId)
        publish(TournamentEventType.CONFIRMATION_RETRACTED, key, round, table)
        return state(table, confirmed - principal.teamId, withdrawals.keys)
    }

    /**
     * Resolves the table, checks the principal is seated there, the tournament is
     * ACTIVE and the principal's team has not withdrawn (ADR 0011), and takes the table
     * lock. Returns the round, the table and the tournament's withdrawn teams.
     */
    private fun lockSeatedTable(key: TableKey, principal: AuthenticatedTeam): Triple<Round, Table, Map<TeamId, Withdrawal>> {
        // The session scopes the request: a foreign tournament is not found, never widened.
        if (key.tournamentId != principal.tournamentId) {
            throw NotFoundException("Tournament ${key.tournamentId.value} not found")
        }
        val round = roundRepository.findByTournamentAndNumber(key.tournamentId, key.roundNumber)
        val table = round?.table(key.tableNumber)
            ?: throw NotFoundException("Table ${key.tableNumber} of round ${key.roundNumber} not found")
        table.requireSeated(principal.teamId)
        val tournament = tournamentRepository.findById(key.tournamentId)
            ?: throw NotFoundException("Tournament ${key.tournamentId.value} not found")
        if (tournament.status != TournamentStatus.ACTIVE) {
            throw TournamentNotActiveException(
                "Tournament ${key.tournamentId.value} is ${tournament.status}; tables are only confirmed while it is active",
            )
        }
        val withdrawals = withdrawalsOf(teamRepository, key.tournamentId)
        if (principal.teamId in withdrawals) {
            throw TeamWithdrawnException("Team ${principal.teamId.value} withdrew; it can no longer confirm or retract")
        }
        roundRepository.lockTable(key)
        return Triple(round, table, withdrawals)
    }

    private fun publish(type: TournamentEventType, key: TableKey, round: Round, table: Table) =
        tournamentEvents.publish(TournamentEvent(type, key.tournamentId, round.id, table.id))

    private fun requireDistinctGameNumbers(seenGames: List<SeenGame>) {
        if (seenGames.map { it.gameNumber }.distinct().size != seenGames.size) {
            throw InvalidFieldException("games", "Each game number may appear only once")
        }
    }

    private fun state(table: Table, confirmed: Set<TeamId>, withdrawn: Set<TeamId>) =
        TableConfirmationState.of(table, confirmed, withdrawn)
}
