package com.example.template.domain

/**
 * The Table Status (GLOSSARY.md, ADR 0006), derived from who confirmed and
 * never stored: no confirmation is [PLAYING], at least one is [CONFIRMING]
 * (games frozen for everyone), every seated team is [COMPLETED] (final).
 */
enum class TableStatus { PLAYING, CONFIRMING, COMPLETED }

/**
 * The confirmations recorded at one [table]: the raw fact (who confirmed) and
 * the [status] derived from it. Completion means "every team seated at the
 * table", so it holds for two seats and for the trio table's three alike.
 */
data class TableConfirmations(
    val table: Table,
    val confirmedTeamIds: Set<TeamId>,
) {
    val status: TableStatus
        get() = when {
            confirmedTeamIds.isEmpty() -> TableStatus.PLAYING
            confirmedTeamIds.containsAll(table.seatedTeams) -> TableStatus.COMPLETED
            else -> TableStatus.CONFIRMING
        }

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
