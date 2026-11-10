package tichu.domain

import java.time.Instant

/**
 * The organizer's last-resort correction of a game at a completed table: who made
 * it and when. A game that was never corrected carries no correction.
 */
data class GameCorrection(
    val adminUserId: AdminUserId,
    val correctedAt: Instant,
)
