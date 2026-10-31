package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.GetTeamScoring
import com.example.template.application.api.RecordedGame
import com.example.template.application.api.RecordedPlayer
import com.example.template.application.api.RecordedTeam
import com.example.template.application.api.TeamTable
import com.example.template.application.api.TeamTablePlayer
import com.example.template.application.api.TeamTableTeam
import com.example.template.domain.Game
import com.example.template.domain.GameOutcome
import com.example.template.domain.GameParticipant
import com.example.template.domain.GameRepository
import com.example.template.domain.GameScoring
import com.example.template.domain.PlayerResult
import com.example.template.domain.Round
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TeamResult
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.NotFoundException

/**
 * The team-session read half of scoring (S5c, #101; ADR 0005). Every read is
 * scoped to the principal's own team: [currentTable] resolves the one round the
 * team is seated at, and [games] reads a table the team is seated at — the seat
 * check mirrors [RecordGameCommand]'s write-side rule. Outcomes are derived from
 * the stored raw facts by the same [GameScoring] engine the write path uses
 * (ADR 0004), so a read and a fresh submit agree by construction.
 */
class GetTeamScoringQuery(
    private val tournamentRepository: TournamentRepository,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
) : GetTeamScoring {

    override fun currentTable(principal: AuthenticatedTeam): TeamTable {
        val tournamentId = principal.tournamentId
        val (round, table) = currentSeating(tournamentId, principal.teamId)
            ?: throw NotFoundException(
                "Team ${principal.teamId.value} is not seated at any round yet",
            )
        val settings = tournamentSettings(tournamentId)

        val teams = listOf(table.home, table.away).map { teamId ->
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
            locked = round.locked,
            allowGrandTichu = settings.allowGrandTichu,
            teams = teams,
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
        val table = round.pairing.tables.firstOrNull { it.id.value == tableNumber }
            ?: throw NotFoundException("Table $tableNumber of round $roundNumber not found")
        requireSeatedAt(principal.teamId, table)

        val settings = tournamentSettings(tournamentId)
        return gameRepository.findByTable(tournamentId, roundNumber, tableNumber)
            .map { game -> toRecordedGame(game, GameScoring.score(toTeamResults(game), settings)) }
    }

    /**
     * The team's in-progress round: the highest-numbered round that seats it.
     * One round is open at a time (ADR 0005), so "current" is unambiguous.
     */
    private fun currentSeating(tournamentId: TournamentId, teamId: TeamId): Pair<Round, Table>? =
        roundRepository.findByTournament(tournamentId)
            .sortedByDescending { it.number }
            .firstNotNullOfOrNull { round ->
                round.pairing.tables.firstOrNull { it.home == teamId || it.away == teamId }
                    ?.let { round to it }
            }

    private fun tournamentSettings(tournamentId: TournamentId) =
        tournamentRepository.findById(tournamentId)?.settings
            ?: throw NotFoundException("Tournament ${tournamentId.value} not found")

    private fun requireTeam(teamId: TeamId, tournamentId: TournamentId): Team =
        teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found")

    private fun requireSeatedAt(teamId: TeamId, table: Table) {
        if (teamId != table.home && teamId != table.away) {
            throw ForbiddenException("Team ${teamId.value} is not seated at table ${table.id.value}")
        }
    }

    /** Reconstructs the engine input from a stored game's raw facts. */
    private fun toTeamResults(game: Game): List<TeamResult> = listOf(
        teamResult(game, game.team1, game.baseScoreTeam1),
        teamResult(game, game.team2, game.baseScoreTeam2),
    )

    private fun teamResult(game: Game, teamId: TeamId, baseScore: Int): TeamResult = TeamResult(
        teamId = teamId,
        baseScore = baseScore,
        players = game.participants
            .filter { it.teamId == teamId }
            .map { PlayerResult(it.playerId, it.position, it.call, it.bombs) },
    )

    /** The full server-authoritative view: stored raw facts + derived outcome. */
    private fun toRecordedGame(game: Game, outcome: GameOutcome): RecordedGame {
        fun GameParticipant.toRecordedPlayer() = RecordedPlayer(
            playerId = playerId,
            position = position,
            call = call,
            bombs = bombs,
            tichuSuccess = playerId in outcome.tichuSuccesses,
        )
        return RecordedGame(
            gameNumber = game.gameNumber,
            version = game.version,
            beschiss = game.beschiss,
            teams = listOf(
                RecordedTeam(
                    game.team1,
                    game.baseScoreTeam1,
                    game.participants.filter { it.teamId == game.team1 }.map { it.toRecordedPlayer() },
                ),
                RecordedTeam(
                    game.team2,
                    game.baseScoreTeam2,
                    game.participants.filter { it.teamId == game.team2 }.map { it.toRecordedPlayer() },
                ),
            ),
            outcome = outcome,
        )
    }
}
