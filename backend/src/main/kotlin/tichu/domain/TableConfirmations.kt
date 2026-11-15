package tichu.domain

/**
 * The Table Status (GLOSSARY.md, ADR 0006), derived from who confirmed and
 * never stored: no confirmation is [PLAYING], at least one is [CONFIRMING]
 * (games frozen for everyone), every seated team is [COMPLETED] (final).
 */
enum class TableStatus { PLAYING, CONFIRMING, COMPLETED }

/**
 * The confirmations recorded at one [table]: the raw fact (who confirmed) and
 * the [status] derived from it. Completion means "every team seated at the
 * table and not withdrawn", so it holds for two seats and for the trio table's
 * three alike, and for a table whose seated teams all withdrew (ADR 0011).
 */
data class TableConfirmations(
    val table: Table,
    val confirmedTeamIds: Set<TeamId>,
    /** Withdrawn teams: their confirmation is no longer needed (ADR 0011). */
    val withdrawnTeamIds: Set<TeamId> = emptySet(),
) {
    /** The seated teams whose confirmation the table still waits for. */
    private val requiredTeamIds: List<TeamId>
        get() = table.seatedTeams - withdrawnTeamIds

    val status: TableStatus
        get() = when {
            requiredTeamIds.all { it in confirmedTeamIds } -> TableStatus.COMPLETED
            confirmedTeamIds.isEmpty() -> TableStatus.PLAYING
            else -> TableStatus.CONFIRMING
        }

    /** Games are frozen from the first confirmation on (ADR 0006). */
    val isFrozen: Boolean
        get() = status != TableStatus.PLAYING

    val isCompleted: Boolean
        get() = status == TableStatus.COMPLETED

    /** The confirmed teams in the table's seat order, the order the API lists them in. */
    val confirmedInSeatOrder: List<TeamId>
        get() = table.seatedTeams.filter { it in confirmedTeamIds }
}

/** A round table's natural key: round and table numbers, as for [GameKey]. */
data class TableKey(
    val tournamentId: TournamentId,
    val roundNumber: Int,
    val tableNumber: Int,
)
