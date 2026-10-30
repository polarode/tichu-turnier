package com.example.template.domain

import com.example.template.vocabulary.GameValidationException

/**
 * A player's pre-deal announcement: none, small Tichu (after first card) or grand
 * Tichu (before any card). Grand Tichu may be disabled by the tournament settings.
 */
enum class TichuCall {
    NONE,
    SMALL,
    GRAND,
}

/**
 * The raw, recorded facts of one player in a game: finish [position] (null when the
 * player never finished, i.e. lost a double win), the [call] made, and the [bombs].
 * Position 1 = first out. Success is never recorded — it is derived (ADR 0004).
 */
data class PlayerResult(
    val playerId: PlayerId,
    val position: Int?,
    val call: TichuCall,
    val bombs: Int,
) {
    /** Derived, never recorded: a call is successful when the player finished first (ADR 0004). */
    val isSuccess: Boolean
        get() = call != TichuCall.NONE && position == 1
}

/**
 * The raw facts of one playing team in a game: its two players' results and the
 * team's [baseScore] (the only score entered; the two teams' base scores sum to 100).
 */
data class TeamResult(
    val teamId: TeamId,
    val baseScore: Int,
    val players: List<PlayerResult>,
)

/**
 * The derived outcome of a validated game (ADR 0004): per-player tichu success,
 * the double-win team if any, per-team total Tichu Points (base + bonuses) and
 * Victory Points. Purely derived — none of this is persisted as fact.
 */
data class GameOutcome(
    val tichuSuccesses: Set<PlayerId>,
    val doubleWinTeam: TeamId?,
    val teamPoints: Map<TeamId, Int>,
    val victoryPoints: Map<TeamId, Int>,
)

/**
 * Pure, framework-free scoring component: raw player facts → [GameOutcome].
 * Validates the game against the v1 rules and derives everything derivable
 * (success, double win, bonuses, TP, VP) in one path. No persistence, no Spring —
 * unit-tested in isolation (issue #99, S5a).
 */
object GameScoring {

    private const val SMALL_TICHU_BONUS = 100
    private const val GRAND_TICHU_BONUS = 200
    private const val DOUBLE_WIN_BONUS = 200

    fun score(teams: List<TeamResult>, settings: TournamentSettings): GameOutcome {
        validateTeams(teams)
        val doubleWinTeam = findDoubleWinTeam(teams)
        validateBaseScores(teams, doubleWinTeam)
        validatePositions(teams, doubleWinTeam)
        validateCalls(teams, settings)

        val successes = teams
            .flatMap { it.players }
            .filter { it.isSuccess }
            .map { it.playerId }
            .toSet()

        val teamPoints = teams.associate { it.teamId to totalPoints(it, doubleWinTeam) }
        val tie = teamPoints.values.distinct().size == 1
        val victoryPoints = teams.associate { team ->
            val vp = when (team.teamId) {
                doubleWinTeam -> 3
                else -> when {
                    tie -> 1
                    teamPoints[team.teamId] == teamPoints.values.max() -> 2
                    else -> 0
                }
            }
            team.teamId to vp
        }

        return GameOutcome(successes, doubleWinTeam, teamPoints, victoryPoints)
    }

    /** The team whose players hold positions 1 and 2, if any. */
    private fun findDoubleWinTeam(teams: List<TeamResult>): TeamId? =
        teams
            .firstOrNull { team -> team.players.mapNotNull { it.position }.sorted() == listOf(1, 2) }
            ?.teamId

    private fun totalPoints(team: TeamResult, doubleWinTeam: TeamId?): Int {
        var points = team.baseScore
        for (player in team.players) {
            points += when {
                player.isSuccess && player.call == TichuCall.GRAND -> GRAND_TICHU_BONUS
                player.isSuccess -> SMALL_TICHU_BONUS
                player.call == TichuCall.GRAND -> -GRAND_TICHU_BONUS
                player.call == TichuCall.SMALL -> -SMALL_TICHU_BONUS
                else -> 0
            }
        }
        if (team.teamId == doubleWinTeam) points += DOUBLE_WIN_BONUS
        return points
    }

    private fun validateTeams(teams: List<TeamResult>) {
        if (teams.size != 2) {
            throw GameValidationException("teams", "A game needs exactly 2 playing teams, got ${teams.size}")
        }
        for (team in teams) {
            if (team.players.size != 2) {
                throw GameValidationException(
                    "players",
                    "Team ${team.teamId.value} must field exactly 2 players, got ${team.players.size}",
                )
            }
            if (team.players.distinctBy { it.playerId }.size != 2) {
                throw GameValidationException(
                    "players",
                    "The two players of team ${team.teamId.value} must be distinct",
                )
            }
        }
        val (first, second) = teams
        val secondIds = second.players.map { it.playerId }.toSet()
        if (first.players.any { it.playerId in secondIds }) {
            throw GameValidationException("players", "A player may not play for both teams")
        }
    }

    private fun validateBaseScores(teams: List<TeamResult>, doubleWinTeam: TeamId?) {
        val expected = if (doubleWinTeam != null) 0 else 100
        if (doubleWinTeam != null) {
            // A double win forces base 0/0 literally — not merely summing to 0.
            if (teams.any { it.baseScore != 0 }) {
                throw GameValidationException("baseScore", "A double win forces base scores 0/0")
            }
        } else if (teams.sumOf { it.baseScore } != expected) {
            throw GameValidationException("baseScore", "The two teams' base scores must sum to 100")
        }
    }

    private fun validatePositions(teams: List<TeamResult>, doubleWinTeam: TeamId?) {
        val positions = teams.flatMap { it.players }.map { it.position }.filterNotNull()
        if (doubleWinTeam != null) {
            // All-or-nothing: winners hold exactly 1 and 2, every loser has no position.
            if (positions != listOf(1, 2)) {
                throw GameValidationException("position", "In a double win the winners hold positions 1 and 2")
            }
        } else if (positions.sorted() != listOf(1, 2, 3, 4)) {
            throw GameValidationException("position", "Positions must be unique 1..4 without gaps")
        }
    }

    private fun validateCalls(teams: List<TeamResult>, settings: TournamentSettings) {
        for (team in teams) {
            for (player in team.players) {
                if (player.call == TichuCall.GRAND && !settings.allowGrandTichu) {
                    throw GameValidationException("call", "Grand Tichu is disabled by the tournament settings")
                }
                if (player.bombs !in 0..3) {
                    throw GameValidationException("bombs", "Bomb count must be between 0 and 3")
                }
            }
        }
    }
}
