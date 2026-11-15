package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TeamId
import tichu.domain.TournamentId

/**
 * Inbound port (driving side) for undoing a Withdrawal (ADR 0011). The principal is the
 * organizer; the lead or a co-organizer may undo, anyone else gets not-found.
 */
interface UndoWithdrawal {

    /**
     * Clears the team's withdrawal. A withdrawal that produced Forfeit Games returns its
     * table to Playing, which removes that table's confirmations. Fails typed with no side
     * effects: not-found for a foreign or unknown tournament or team,
     * [tichu.vocabulary.TeamNotWithdrawnException] for a team that is not withdrawn, and
     * [tichu.vocabulary.WithdrawalFinalException] once the withdrawal is final.
     */
    fun undo(
        tournamentId: TournamentId,
        teamId: TeamId,
        admin: AdminUserId,
    )
}
