package tichu.application.usecase

import tichu.application.api.ListRounds
import tichu.application.api.RecordedGame
import tichu.application.api.RoundOverview
import tichu.application.api.TableConfirmationState
import tichu.application.api.TableOverview
import tichu.domain.AdminUserId
import tichu.domain.GameRepository
import tichu.domain.RoundRepository
import tichu.domain.TableConfirmationRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository

/**
 * The admin's rounds read: every round with each table's entered games,
 * scored by [RecordedGame.of] — the same path the team reads and the write
 * responses take. Owner-scoped; there is no admin write path for games.
 */
class ListRoundsQuery(
    private val tournamentAccess: TournamentAccess,
    private val roundRepository: RoundRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
) : ListRounds {
    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<RoundOverview> {
        val settings = tournamentAccess.organizedOrThrow(tournamentId, owner).settings
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
