package tichu.domain

import java.time.Instant

/**
 * Outbound port (driven side of the hexagon) for the per-team confirmations of a
 * round table (ADR 0006): one record per confirming team, addressed by the
 * table's natural [TableKey]. The status is derived by [TableConfirmations].
 */
interface TableConfirmationRepository {

    /** The teams that confirmed the table; empty when none did. */
    fun findByTable(key: TableKey): Set<TeamId>

    /** The confirmed teams of every table in a round, by table; tables without any are absent. */
    fun findByRound(tournamentId: TournamentId, roundNumber: Int): Map<TableId, Set<TeamId>>

    /** Records [teamId]'s confirmation of the table at [confirmedAt]. */
    fun add(key: TableKey, teamId: TeamId, confirmedAt: Instant)

    /** Removes [teamId]'s confirmation of the table, if present. */
    fun remove(key: TableKey, teamId: TeamId)
}
