package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.ConfirmTable
import com.example.template.application.api.SeenGame
import com.example.template.application.api.TableConfirmationState
import com.example.template.domain.GameRepository
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.TableConfirmationRepository
import com.example.template.domain.TableConfirmations
import com.example.template.domain.TableKey
import com.example.template.domain.TableStatus
import com.example.template.domain.TeamId
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.GameConcurrentModificationException
import com.example.template.vocabulary.InvalidFieldException
import com.example.template.vocabulary.NotFoundException
import com.example.template.vocabulary.TableCompletedException
import com.example.template.vocabulary.TableIncompleteException
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
 * Confirm checks, in order: seat (403), already confirmed (no change), every game
 * entered ([TableIncompleteException]), then the seen versions
 * ([GameConcurrentModificationException] on any difference).
 */
@Transactional
class ConfirmTableCommand(
    private val roundRepository: RoundRepository,
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
        if (TableConfirmations(table, confirmed).status == TableStatus.COMPLETED) {
            throw TableCompletedException("Table ${table.id.value} is completed; confirmations are final")
        }
        if (principal.teamId !in confirmed) return state(table, confirmed)

        confirmationRepository.remove(key, principal.teamId)
        return state(table, confirmed - principal.teamId)
    }

    /** Resolves the table, checks the principal is seated there, and takes the table lock. */
    private fun lockSeatedTable(key: TableKey, principal: AuthenticatedTeam): Table {
        // The session scopes the request: a foreign tournament is not found, never widened.
        if (key.tournamentId != principal.tournamentId) {
            throw NotFoundException("Tournament ${key.tournamentId.value} not found")
        }
        val table = roundRepository.findByTournamentAndNumber(key.tournamentId, key.roundNumber)
            ?.table(key.tableNumber)
            ?: throw NotFoundException("Table ${key.tableNumber} of round ${key.roundNumber} not found")
        if (!table.seats(principal.teamId)) {
            throw ForbiddenException("Team ${principal.teamId.value} is not seated at table ${table.id.value}")
        }
        roundRepository.lockTable(key)
        return table
    }

    private fun requireDistinctGameNumbers(seenGames: List<SeenGame>) {
        if (seenGames.map { it.gameNumber }.distinct().size != seenGames.size) {
            throw InvalidFieldException("games", "Each game number may appear only once")
        }
    }

    private fun state(table: Table, confirmed: Set<TeamId>) =
        TableConfirmationState.of(TableConfirmations(table, confirmed))
}
