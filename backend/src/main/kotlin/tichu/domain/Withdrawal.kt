package tichu.domain

import java.time.Instant

/**
 * Why a team withdrew (GLOSSARY.md: **Withdrawal**, ADR 0011): [DISQUALIFIED] by the
 * organizer, or [VOLUNTARY] by the team's own choice. Visible to everyone.
 */
enum class WithdrawalReason { DISQUALIFIED, VOLUNTARY }

/**
 * The Forfeit Compensation (GLOSSARY.md, ADR 0011) the organizer picks per
 * withdrawal: the Victory and Tichu Points a Forfeit Game awards the remaining team.
 */
enum class ForfeitCompensation(val victoryPoints: Int, val tichuPoints: Int) {
    FULL(2, 100),
    HALF(1, 50),
    NONE(0, 0),
}

/**
 * A team's Withdrawal (GLOSSARY.md, ADR 0011): the [reason], the
 * [compensation] for the games it still owed, the [roundNumber] its current table
 * belonged to when it withdrew, and who recorded it and when.
 */
data class Withdrawal(
    val reason: WithdrawalReason,
    val compensation: ForfeitCompensation,
    val roundNumber: Int,
    val withdrawnAt: Instant,
    val withdrawnBy: AdminUserId,
) {
    /** Whether the organizer disqualified the team: such a team never ranks or leads a Highlight (ADR 0011). */
    val isDisqualified: Boolean
        get() = reason == WithdrawalReason.DISQUALIFIED
}

/**
 * A Forfeit Game (GLOSSARY.md, ADR 0011): a game at [tableNumber] of [roundNumber]
 * that was never played because [withdrawnTeam] withdrew. The [opponent] is the
 * other team of the game; it is [awarded] the game at the [compensation] unless it
 * withdrew too, in which case nobody is. Counts toward VP and TP only; both teams
 * of the game appear in [outcome].
 */
data class ForfeitGame(
    val roundNumber: Int,
    val tableNumber: Int,
    val gameNumber: Int,
    val withdrawnTeam: TeamId,
    val opponent: TeamId,
    val awarded: Boolean,
    val compensation: ForfeitCompensation,
) {
    /** The team the game is awarded to, or null when nobody is. */
    val awardedTo: TeamId?
        get() = if (awarded) opponent else null

    /**
     * The game's VP and TP for both playing teams: the awarded team gets the
     * [compensation], the withdrawn team and an unawarded game give 0. No tichu
     * successes and no double win, as no cards were played.
     */
    fun outcome(): GameOutcome {
        val awardedPoints = if (awarded) compensation else null
        return GameOutcome(
            tichuSuccesses = emptySet(),
            doubleWinTeam = null,
            teamPoints = mapOf(
                opponent to (awardedPoints?.tichuPoints ?: 0),
                withdrawnTeam to 0,
            ),
            victoryPoints = mapOf(
                opponent to (awardedPoints?.victoryPoints ?: 0),
                withdrawnTeam to 0,
            ),
        )
    }
}
