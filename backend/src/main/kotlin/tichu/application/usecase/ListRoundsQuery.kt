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
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository

/**
 * The admin's rounds read: every round with each table's entered games,
 * scored by [RecordedGame.of] — the same path the team reads and the write
 * responses take. Owner-scoped; there is no admin write path for games. A table's
 * Table Status counts withdrawn teams as not needing a confirmation (ADR 0011).
 */
class ListRoundsQuery(
    private val tournamentAccess: TournamentAccess,
    private val roundRepository: RoundRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
    private val teamRepository: TeamRepository,
) : ListRounds {
    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<RoundOverview> {
        val settings = tournamentAccess.organizedOrThrow(tournamentId, owner).settings
        val withdrawals = withdrawalsOf(teamRepository, tournamentId)
        return roundRepository.findByTournament(tournamentId).map { round ->
            val gamesByTable = gameRepository.findByRound(tournamentId, round.number).groupBy { it.tableNumber }
            val confirmedByTable = confirmationRepository.findByRound(tournamentId, round.number)
            RoundOverview(
                round = round,
                tables = round.pairing.tables.map { table ->
                    val games = gamesByTable[table.id.value].orEmpty()
                    TableOverview(
                        table = table,
                        games = games.map { RecordedGame.of(it, settings) },
                        confirmation = TableConfirmationState.of(
                            table,
                            confirmedByTable[table.id].orEmpty(),
                            withdrawals.keys,
                        ),
                        forfeits = tableForfeits(round, table, games, withdrawals),
                        withdrawnTeamIds = table.seatedTeams.filter { it in withdrawals },
                    )
                },
            )
        }
    }
}
