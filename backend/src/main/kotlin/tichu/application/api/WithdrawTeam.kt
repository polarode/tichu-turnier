package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.ForfeitCompensation
import tichu.domain.TeamId
import tichu.domain.TournamentId
import tichu.domain.Withdrawal
import tichu.domain.WithdrawalReason

/**
 * Inbound port (driving side) for the organizer's Withdrawal of a team (ADR 0011). The
 * principal is the organizer; the lead or a co-organizer may withdraw, anyone else gets
 * not-found.
 */
interface WithdrawTeam {

    /**
     * Records the team's withdrawal with [reason] and [compensation], stamped with the
     * highest drawn round, the instant and [admin]. Fails typed with no side effects:
     * not-found for a foreign or unknown tournament or team,
     * [tichu.vocabulary.TournamentNotStartedException] in setup, and
     * [tichu.vocabulary.TeamAlreadyWithdrawnException] for a team already withdrawn.
     */
    fun withdraw(
        tournamentId: TournamentId,
        teamId: TeamId,
        reason: WithdrawalReason,
        compensation: ForfeitCompensation,
        admin: AdminUserId,
    ): Withdrawal
}
