package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.GameWriteRequest
import com.example.template.application.api.RecordGame
import com.example.template.application.api.RecordedGame
import com.example.template.application.api.RecordedPlayer
import com.example.template.application.api.RecordedTeam
import com.example.template.domain.Game
import com.example.template.domain.GameKey
import com.example.template.domain.GameOutcome
import com.example.template.domain.GameParticipant
import com.example.template.domain.GameRepository
import com.example.template.domain.GameScoring
import com.example.template.domain.PlayerResult
import com.example.template.domain.Round
import com.example.template.domain.RoundRepository
import com.example.template.domain.Table
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TeamResult
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.GameConfirmedException
import com.example.template.vocabulary.GameConcurrentModificationException
import com.example.template.vocabulary.GameNotFoundException
import com.example.template.vocabulary.GameValidationException
import com.example.template.vocabulary.InvalidFieldException
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
 * its four participants commit together or nothing is written. Versioning is
 * optimistic: create assigns version 1, update carries the version and the first
 * commit wins.
 */
@Transactional
class RecordGameCommand(
    private val tournamentRepository: TournamentRepository,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val gameRepository: GameRepository,
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
        requireGameNumber(request.gameNumber)

        val round = roundRepository.findByTournamentAndNumber(tournamentId, roundNumber)
            ?: throw GameNotFoundException(
                "Round $roundNumber of tournament ${tournamentId.value} not found",
            )
        if (round.locked) {
            throw GameConfirmedException("Round ${round.number} is locked; its games are read-only")
        }
        val table = requireTable(round, tableNumber)
        requireSeatedAt(principal.teamId, table)

        val tournament = tournamentRepository.findById(tournamentId)
            ?: throw GameNotFoundException("Tournament ${tournamentId.value} not found")

        val teamResults = toTeamResults(request)
        requirePlayingTeams(tournamentId, teamResults, listOf(table.home, table.away))

        val outcome = GameScoring.score(teamResults, tournament.settings)

        val baseGame = Game(
            id = null,
            tournamentId = tournamentId,
            roundNumber = round.number,
            tableNumber = table.id.value,
            gameNumber = request.gameNumber,
            team1 = teamResults[0].teamId,
            team2 = teamResults[1].teamId,
            baseScoreTeam1 = teamResults[0].baseScore,
            baseScoreTeam2 = teamResults[1].baseScore,
            beschiss = request.beschiss,
            version = 0, // assigned by the adapter: 1 on create, +1 per update
            participants = teamResults.flatMap { team ->
                team.players.map {
                    GameParticipant(it.playerId, team.teamId, it.position, it.call, it.bombs)
                }
            },
        )

        val recorded = if (create) {
            gameRepository.create(baseGame)
        } else {
            val key = GameKey(tournamentId, round.number, table.id.value, request.gameNumber)
            // Existence first, so a missing game answers 404 — the stale-version
            // race answers 409 from the conditional update below.
            gameRepository.find(key)
                ?: throw GameNotFoundException(
                "Game ${request.gameNumber} at table ${table.id.value} of round ${round.number} not found",
            )
            gameRepository.updateWithVersion(key, request.version!!) {
                baseGame.copy(id = it.id, version = it.version)
            }
                ?: throw GameConcurrentModificationException(
                    "Game ${request.gameNumber} was modified concurrently; reload and retry",
                )
        }

        return toRecordedGame(recorded, outcome)
    }

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

    private fun requireVersionOnUpdate(create: Boolean, request: GameWriteRequest) {
        if (create && request.version != null) {
            throw InvalidFieldException("version", "Version is only sent when editing an existing game")
        }
        if (!create && request.version == null) {
            throw InvalidFieldException("version", "Editing a game requires the version it was read with")
        }
    }

    private fun requireGameNumber(gameNumber: Int) {
        if (gameNumber !in 1..4) {
            throw InvalidFieldException("gameNumber", "Game number must be between 1 and 4")
        }
    }

    private fun requireTable(round: Round, tableNumber: Int): Table =
        round.pairing.tables.firstOrNull { it.id.value == tableNumber }
            ?: throw GameNotFoundException(
                "Table $tableNumber of round ${round.number} not found",
            )

    private fun requireSeatedAt(teamId: TeamId, table: Table) {
        if (teamId != table.home && teamId != table.away) {
            throw ForbiddenException("Team ${teamId.value} is not seated at table ${table.id.value}")
        }
    }

    private fun requirePlayingTeams(
        tournamentId: TournamentId,
        teamResults: List<TeamResult>,
        playingTeams: List<TeamId>,
    ) {
        if (teamResults.size != 2) {
            throw GameValidationException("teams", "A game has exactly 2 playing teams, got ${teamResults.size}")
        }
        if (teamResults.map { it.teamId }.toSet() != playingTeams.toSet()) {
            throw GameValidationException(
                "teams",
                "The submitted teams must be the table's playing teams ${playingTeams.map { it.value }}",
            )
        }
        // The participants must be exactly the playing teams' players: each
        // submitted player has to belong to the team it is grouped under.
        for (team in teamResults) {
            val roster = teamRepository.findById(team.teamId, tournamentId)
                ?: throw GameValidationException("teams", "Team ${team.teamId.value} is unknown in this tournament")
            val rosterIds = roster.players.map { it.id }.toSet()
            if (team.players.any { it.playerId !in rosterIds }) {
                throw GameValidationException(
                    "players",
                    "All participants must be players of team ${team.teamId.value}",
                )
            }
        }
    }

    private fun toTeamResults(request: GameWriteRequest): List<TeamResult> =
        request.teams.map { team ->
            TeamResult(
                teamId = team.teamId,
                baseScore = team.baseScore,
                players = team.players.map { PlayerResult(it.playerId, it.position, it.call, it.bombs) },
            )
        }
}
