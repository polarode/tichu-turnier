package tichu.infra.rest.web

import tichu.application.api.PlayerResultInput
import tichu.application.api.RecordedGame
import tichu.application.api.RecordedPlayer
import tichu.application.api.RecordedTeam
import tichu.application.api.TeamResultInput
import tichu.domain.GameCorrection
import tichu.domain.GameOutcome
import tichu.domain.PlayerId
import tichu.domain.TichuCall
import tichu.domain.TeamId
import tichu.domain.TournamentId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Wire format of the scoring endpoints (#100). The DTO mirrors the team-grouped
 * engine input exactly (raw facts only — no totals, no success, no double-win
 * flag, ADR 0004); the mapping to the transport-agnostic [tichu.application.api.GameWriteRequest]
 * is explicit so the use case never sees HTTP types.
 */
data class GameRequestDto(
    @field:NotNull
    // The upper bound depends on the table (4, or 6 at a trio table); the use case checks it.
    @field:Min(value = 1, message = "must be at least 1")
    val gameNumber: Int,
    @field:NotNull
    val beschiss: Boolean,
    /** PUT only — the optimistic-locking version the game was read with. */
    val version: Int?,
    @field:NotNull
    @field:Size(min = 2, max = 2, message = "a game has exactly 2 playing teams")
    val teams: List<TeamResultDto>,
) {
    fun toWriteRequest(): tichu.application.api.GameWriteRequest =
        tichu.application.api.GameWriteRequest(
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
    /** Set when an organizer corrected this game at a completed table; null otherwise. */
    val correction: GameCorrectionDto?,
) {
    companion object {
        fun from(recorded: RecordedGame): RecordedGameDto = RecordedGameDto(
            gameNumber = recorded.gameNumber,
            version = recorded.version,
            beschiss = recorded.beschiss,
            teams = recorded.teams.map(RecordedTeamDto::from),
            outcome = GameOutcomeDto.from(recorded.outcome),
            correction = recorded.correction?.let(GameCorrectionDto::from),
        )
    }
}

/** The organizer's correction stamp: the correcting admin's user id and the instant. */
data class GameCorrectionDto(
    val correctedBy: String,
    val correctedAt: Instant,
) {
    companion object {
        fun from(correction: GameCorrection): GameCorrectionDto = GameCorrectionDto(
            correctedBy = correction.adminUserId.value,
            correctedAt = correction.correctedAt,
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
