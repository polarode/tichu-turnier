package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GetTeamScoring
import tichu.application.api.RecordedGame
import tichu.application.api.TableConfirmationState
import tichu.application.api.TeamTable
import tichu.application.api.TeamTablePlayer
import tichu.application.api.TeamTableTeam
import tichu.domain.GameRepository
import tichu.domain.Round
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.NotFoundException

/**
 * The team-session read half of scoring (S5c, #101; ADR 0005). Every read is
 * scoped to the principal's own team: [currentTable] resolves the one round the
 * team is seated at, and [games] reads a table the team is seated at — the seat
 * check mirrors [RecordGameCommand]'s write-side rule. Outcomes are derived from
 * the stored raw facts by [RecordedGame.of], the same path the write side
 * answers with (ADR 0004), so a read and a fresh submit agree by construction.
 */
class GetTeamScoringQuery(
    private val tournamentRepository: TournamentRepository,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
) : GetTeamScoring {

    override fun currentTable(principal: AuthenticatedTeam): TeamTable {
        val tournamentId = principal.tournamentId
        val (round, table) = currentSeating(tournamentId, principal.teamId)
            ?: throw NotFoundException(
                "Team ${principal.teamId.value} is not seated at any round yet",
            )
        val settings = tournamentSettings(tournamentId)

        val teams = table.seatedTeams.map { teamId ->
            val team = requireTeam(teamId, tournamentId)
            TeamTableTeam(
                teamId = team.id,
                teamName = team.name,
                players = team.players.map { TeamTablePlayer(it.id, it.name) },
            )
        }

        return TeamTable(
            tournamentId = tournamentId,
            roundNumber = round.number,
            tableNumber = table.id.value,
            confirmation = TableConfirmationState.of(
                table,
                confirmationRepository.findByTable(TableKey(tournamentId, round.number, table.id.value)),
            ),
            grandTichuBonus = settings.grandTichuBonus,
            doubleWinBonus = settings.doubleWinBonus,
            teams = teams,
            trio = table.isTrio,
            schedule = table.schedule,
        )
    }

    override fun games(
        principal: AuthenticatedTeam,
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
    ): List<RecordedGame> {
        // The session scopes the read: a path that names a foreign tournament is
        // treated as not-found, never widened to another tournament's games.
        if (tournamentId != principal.tournamentId) {
            throw NotFoundException("Tournament ${tournamentId.value} not found")
        }
        val round = roundRepository.findByTournamentAndNumber(tournamentId, roundNumber)
            ?: throw NotFoundException("Round $roundNumber of tournament ${tournamentId.value} not found")
        val table = round.table(tableNumber)
            ?: throw NotFoundException("Table $tableNumber of round $roundNumber not found")
        table.requireSeated(principal.teamId)

        val settings = tournamentSettings(tournamentId)
        return gameRepository.findByTable(tournamentId, roundNumber, tableNumber)
            .map { RecordedGame.of(it, settings) }
    }

    /**
     * The team's in-progress round: the highest-numbered round that seats it.
     * One round is open at a time (ADR 0005), so "current" is unambiguous.
     */
    private fun currentSeating(tournamentId: TournamentId, teamId: TeamId): Pair<Round, Table>? =
        roundRepository.findByTournament(tournamentId)
            .sortedByDescending { it.number }
            .firstNotNullOfOrNull { round -> round.tableOf(teamId)?.let { round to it } }

    private fun tournamentSettings(tournamentId: TournamentId) =
        tournamentRepository.findById(tournamentId)?.settings
            ?: throw NotFoundException("Tournament ${tournamentId.value} not found")

    private fun requireTeam(teamId: TeamId, tournamentId: TournamentId): Team =
        teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found")

}
