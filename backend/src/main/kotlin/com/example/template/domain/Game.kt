package com.example.template.domain

/**
 * One played game at a round table: the recorded raw facts (ADR 0004) of the
 * two playing teams — [team1], [team2], their base scores and each player's
 * [participants] — plus the [beschiss] marker. Created lazily on the first
 * submit for its slot; the pairing flow never pre-creates games.
 *
 * The natural key is `(tournamentId, roundNumber, tableNumber, gameNumber)`
 * (table numbers, not surrogate table ids — the pairing re-saves tables
 * wholesale). Everything derivable (success, double win, bonuses, totals,
 * victory points) is derived on read by [GameScoring], never stored.
 */
data class Game(
    /** Surrogate database id; assigned by the sequence, internal only. */
    val id: Long?,
    val tournamentId: TournamentId,
    val roundNumber: Int,
    val tableNumber: Int,
    /** 1-based game number within the table (1–4; trio 1–6 with #32). */
    val gameNumber: Int,
    val team1: TeamId,
    val team2: TeamId,
    val baseScoreTeam1: Int,
    val baseScoreTeam2: Int,
    /** Playful table accusation; a pure marker with no score effect (v1 parity). */
    val beschiss: Boolean,
    /** Optimistic-locking counter: starts at 1 on create, increments on update. */
    val version: Int,
    val participants: List<GameParticipant>,
) {
    init {
        require(team1 != team2) { "The two playing teams of a game must be distinct" }
        require(participants.size == 4) { "A game has exactly 4 participants, got ${participants.size}" }
    }
}

/**
 * One player's recorded facts in a [Game]: finish [position] (null when the
 * player never finished, i.e. lost a double win), the Tichu [call] made, and
 * the bomb count. [teamId] is denormalized — the player's team within this game.
 */
data class GameParticipant(
    val playerId: PlayerId,
    val teamId: TeamId,
    val position: Int?,
    val call: TichuCall,
    val bombs: Int,
)
