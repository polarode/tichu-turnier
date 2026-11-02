package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Round
import com.example.template.domain.Table
import com.example.template.domain.TournamentId

interface ListRounds {
    /** Fails as not-found when the tournament does not exist or is owned by another admin. */
    fun list(tournamentId: TournamentId, owner: AdminUserId): List<RoundOverview>
}

/**
 * A round as the admin follows it (S5d, #102): the pairing plus, per table,
 * the games entered so far — read-only, scored server-side (ADR 0004).
 */
data class RoundOverview(
    val round: Round,
    val tables: List<TableOverview>,
)

/** One table of a [RoundOverview]: the seating, its entered games (by game number) and its confirmation state. */
data class TableOverview(
    val table: Table,
    val games: List<RecordedGame>,
    val confirmation: TableConfirmationState,
)
