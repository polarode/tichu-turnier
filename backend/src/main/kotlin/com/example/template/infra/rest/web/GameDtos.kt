package com.example.template.infra.rest.web

import com.example.template.application.api.PlayerResultInput
import com.example.template.application.api.RecordedGame
import com.example.template.application.api.RecordedPlayer
import com.example.template.application.api.RecordedTeam
import com.example.template.application.api.TeamResultInput
import com.example.template.domain.GameOutcome
import com.example.template.domain.PlayerId
import com.example.template.domain.TichuCall
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * Wire format of the scoring endpoints (#100). The DTO mirrors the team-grouped
 * engine input exactly (raw facts only — no totals, no success, no double-win
 * flag, ADR 0004); the mapping to the transport-agnostic [com.example.template.application.api.GameWriteRequest]
 * is explicit so the use case never sees HTTP types.
 */
data class GameRequestDto(
    @field:NotNull
    @field:Min(value = 1, message = "must be at least 1")
    @field:Max(value = 4, message = "must be at most 4")
    val gameNumber: Int,
    @field:NotNull
    val beschiss: Boolean,
    /** PUT only — the optimistic-locking version the game was read with. */
    val version: Int?,
    @field:NotNull
    @field:Size(min = 2, max = 2, message = "a game has exactly 2 playing teams")
    val teams: List<TeamResultDto>,
) {
    fun toWriteRequest(): com.example.template.application.api.GameWriteRequest =
        com.example.template.application.api.GameWriteRequest(
            gameNumber = gameNumber,
            beschiss = beschiss,
            version = version,
            teams = teams.map { it.toInput() },
        )
}

data class TeamResultDto(
    @field:NotNull
    val teamId: String,
    @field:NotNull
    val baseScore: Int,
    @field:NotNull
    @field:Size(min = 2, max = 2, message = "a playing team fields exactly 2 players")
    val players: List<PlayerResultDto>,
) {
    fun toInput(): TeamResultInput = TeamResultInput(
        teamId = TeamId(teamId),
        baseScore = baseScore,
        players = players.map { it.toInput() },
    )
}

data class PlayerResultDto(
    @field:NotNull
    val playerId: String,
    /** Null when the player never finished (lost a double win). */
    val position: Int?,
    @field:NotNull
    val call: TichuCall,
    @field:NotNull
    @field:Min(value = 0, message = "must be at least 0")
    @field:Max(value = 3, message = "must be at most 3")
    val bombs: Int,
) {
    fun toInput(): PlayerResultInput = PlayerResultInput(
        playerId = PlayerId(playerId),
        position = position,
        call = call,
        bombs = bombs,
    )
}

/** The server-authoritative answer: full game echo + derived outcome (ADR 0004). */
data class RecordedGameDto(
    val gameNumber: Int,
    val version: Int,
    val beschiss: Boolean,
    val teams: List<RecordedTeamDto>,
    val outcome: GameOutcomeDto,
) {
    companion object {
        fun from(recorded: RecordedGame): RecordedGameDto = RecordedGameDto(
            gameNumber = recorded.gameNumber,
            version = recorded.version,
            beschiss = recorded.beschiss,
            teams = recorded.teams.map(RecordedTeamDto::from),
            outcome = GameOutcomeDto.from(recorded.outcome),
        )
    }
}

data class RecordedTeamDto(
    val teamId: String,
    val baseScore: Int,
    val players: List<RecordedPlayerDto>,
) {
    companion object {
        fun from(team: RecordedTeam): RecordedTeamDto = RecordedTeamDto(
            teamId = team.teamId.value,
            baseScore = team.baseScore,
            players = team.players.map(RecordedPlayerDto::from),
        )
    }
}

data class RecordedPlayerDto(
    val playerId: String,
    val position: Int?,
    val call: TichuCall,
    val bombs: Int,
    /** Derived server-side: call ∧ position 1 (ADR 0004). */
    val tichuSuccess: Boolean,
) {
    companion object {
        fun from(player: RecordedPlayer): RecordedPlayerDto = RecordedPlayerDto(
            playerId = player.playerId.value,
            position = player.position,
            call = player.call,
            bombs = player.bombs,
            tichuSuccess = player.tichuSuccess,
        )
    }
}

data class GameOutcomeDto(
    val tichuSuccesses: List<String>,
    val doubleWinTeam: String?,
    val teamPoints: Map<String, Int>,
    val victoryPoints: Map<String, Int>,
) {
    companion object {
        fun from(outcome: GameOutcome): GameOutcomeDto = GameOutcomeDto(
            tichuSuccesses = outcome.tichuSuccesses.map { it.value }.sorted(),
            doubleWinTeam = outcome.doubleWinTeam?.value,
            teamPoints = outcome.teamPoints.mapKeys { it.key.value },
            victoryPoints = outcome.victoryPoints.mapKeys { it.key.value },
        )
    }
}
