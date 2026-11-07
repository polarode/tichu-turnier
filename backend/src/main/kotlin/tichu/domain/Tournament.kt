package tichu.domain

import java.time.Instant

/**
 * The core domain entity of the tournament setup phase. Framework-free: no Spring,
 * no persistence annotations.
 *
 * [ownerId] is the admin who created it: every read is scoped to that owner, so a
 * tournament can never be loaded or changed by another admin's session.
 */
data class Tournament(
    val id: TournamentId,
    val ownerId: AdminUserId,
    val name: String,
    val description: String,
    val maxTeams: Int,
    val settings: TournamentSettings,
    val status: TournamentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Whether the tournament is shown publicly. Off by default; a plain flag, not a setting. */
    val publicVisibility: Boolean = false,
) {
    fun isOwnedBy(owner: AdminUserId): Boolean = ownerId == owner
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class TournamentId(val value: String)

/**
 * Status machine: `setup → active → completed → published`, each step one-way, no
 * reopen. Each transition is enforced by its use case (start, finish, publish); a
 * newly created tournament always starts in [SETUP].
 */
enum class TournamentStatus {
    SETUP,
    ACTIVE,
    COMPLETED,
    PUBLISHED,
    ;

    companion object {
        /**
         * The statuses a public tournament is shown under, in public-home display order.
         * SETUP is excluded: a tournament that has not started is never listed publicly.
         */
        val PUBLICLY_LISTABLE: List<TournamentStatus> = listOf(ACTIVE, COMPLETED, PUBLISHED)
    }
}

/**
 * Organizer-configurable tournament rules, stored as one unit and validated
 * server-side. New settings belong here, not as extra entity columns scattered
 * across the domain.
 */
data class TournamentSettings(
    /**
     * Tichu Points a grand Tichu call wins or loses: 200 under the normal rules, or
     * 100 to score it like a small Tichu. One of [GRAND_TICHU_BONUS_OPTIONS].
     */
    val grandTichuBonus: Int = 200,
    /**
     * Tichu Points the team earns for a double win: 200 under the normal rules, or
     * 100 for a smaller reward. One of [DOUBLE_WIN_BONUS_OPTIONS]. The extra victory
     * point is unaffected.
     */
    val doubleWinBonus: Int = 200,
    /**
     * The Total Rounds (GLOSSARY.md): how many rounds the tournament runs, within
     * [TOTAL_ROUNDS_RANGE]. The one setting still adjustable while ACTIVE.
     */
    val totalRounds: Int = 4,
) {
    companion object {
        val GRAND_TICHU_BONUS_OPTIONS = setOf(100, 200)
        val DOUBLE_WIN_BONUS_OPTIONS = setOf(100, 200)
        val TOTAL_ROUNDS_RANGE = 1..20
    }
}
