package com.example.template.application.api

import com.example.template.domain.TableConfirmations
import com.example.template.domain.TableKey
import com.example.template.domain.TableStatus
import com.example.template.domain.TeamId

/**
 * Inbound port (driving side) for the per-table confirmation workflow (S6a, #105;
 * ADR 0006). The principal is the team, so the session — not the request —
 * decides who confirms or retracts; only a team seated at the table may.
 * Requests that would change nothing (confirm when already confirmed, retract
 * when not confirmed) succeed with the current state.
 */
interface ConfirmTable {

    /**
     * Confirms every game at the table for the principal's team. [seenGames] are
     * the games the team confirms, as it last read them; they must match the
     * stored games exactly, so a confirmation never covers a game the team
     * didn't see.
     */
    fun confirm(key: TableKey, seenGames: List<SeenGame>, principal: AuthenticatedTeam): TableConfirmationState

    /** Withdraws the principal team's confirmation; rejected once the table is completed. */
    fun retract(key: TableKey, principal: AuthenticatedTeam): TableConfirmationState
}

/** One game as the confirming team last read it. */
data class SeenGame(val gameNumber: Int, val version: Int)

/** A table's confirmation state: the derived [status] and the confirmed teams in seat order. */
data class TableConfirmationState(
    val status: TableStatus,
    val confirmedTeamIds: List<TeamId>,
) {
    companion object {
        fun of(confirmations: TableConfirmations): TableConfirmationState =
            TableConfirmationState(confirmations.status, confirmations.confirmedInSeatOrder)
    }
}
