package com.example.template.application.usecase

import com.example.template.application.api.ListRounds
import com.example.template.application.api.RecordedGame
import com.example.template.application.api.RoundOverview
import com.example.template.application.api.TableConfirmationState
import com.example.template.application.api.TableOverview
import com.example.template.domain.AdminUserId
import com.example.template.domain.GameRepository
import com.example.template.domain.RoundRepository
import com.example.template.domain.TableConfirmationRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository

/**
 * The admin's rounds read: every round with each table's entered games,
 * scored by [RecordedGame.of] — the same path the team reads and the write
 * responses take. Owner-scoped; there is no admin write path for games.
 */
class ListRoundsQuery(
    private val tournamentRepository: TournamentRepository,
    private val roundRepository: RoundRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
) : ListRounds {
    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<RoundOverview> {
        val settings = tournamentRepository.findOwnedOrThrow(tournamentId, owner).settings
        return roundRepository.findByTournament(tournamentId).map { round ->
            val gamesByTable = gameRepository.findByRound(tournamentId, round.number).groupBy { it.tableNumber }
            val confirmedByTable = confirmationRepository.findByRound(tournamentId, round.number)
            RoundOverview(
                round = round,
                tables = round.pairing.tables.map { table ->
                    TableOverview(
                        table = table,
                        games = gamesByTable[table.id.value].orEmpty().map { RecordedGame.of(it, settings) },
                        confirmation = TableConfirmationState.of(table, confirmedByTable[table.id].orEmpty()),
                    )
                },
            )
        }
    }
}
