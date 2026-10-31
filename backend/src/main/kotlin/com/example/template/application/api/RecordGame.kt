package com.example.template.application.api

import com.example.template.domain.GameOutcome
import com.example.template.domain.PlayerId
import com.example.template.domain.TichuCall
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/**
 * Transport-agnostic write payload for recording a game (issue #100): raw facts
 * only, team-grouped exactly as the scoring engine wants them. No client totals,
 * no tichu_success, no doubleWin flag — everything derivable is derived
 * server-side (ADR 0004). [version] is carried on PUT only; POST has none.
 */
data class GameWriteRequest(
    val gameNumber: Int,
    val beschiss: Boolean,
    val version: Int?,
    val teams: List<TeamResultInput>,
)

data class TeamResultInput(
    val teamId: TeamId,
    val baseScore: Int,
    val players: List<PlayerResultInput>,
)

data class PlayerResultInput(
    val playerId: PlayerId,
    val position: Int?,
    val call: TichuCall,
    val bombs: Int,
)

/**
 * The server-authoritative answer after a successful submit: the full game —
 * natural key, raw facts as stored (the participants echo) with the derived
 * per-player success — plus the derived outcome.
 */
data class RecordedGame(
    val gameNumber: Int,
    val version: Int,
    val beschiss: Boolean,
    val teams: List<RecordedTeam>,
    val outcome: GameOutcome,
)

data class RecordedTeam(
    val teamId: TeamId,
    val baseScore: Int,
    val players: List<RecordedPlayer>,
)

data class RecordedPlayer(
    val playerId: PlayerId,
    val position: Int?,
    val call: TichuCall,
    val bombs: Int,
    /** Derived, never stored: call ∧ position 1 (ADR 0004). */
    val tichuSuccess: Boolean,
)

/**
 * Inbound port (driving side) for the team-session scoring API. The round and
 * table are addressed by numbers (the API never takes a surrogate table id);
 * the principal is the team, so the session — not the request — scopes the
 * mutation. Authorization is "any team seated at the table may create or edit
 * any game at that table", checked inside the use case.
 */
interface RecordGame {

    /** Creates the game; a natural-key slot that already holds one is a conflict. */
    fun create(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        principal: AuthenticatedTeam,
    ): RecordedGame

    /** Explicit edit; carries [GameWriteRequest.version] for optimistic locking. */
    fun update(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        principal: AuthenticatedTeam,
    ): RecordedGame
}
