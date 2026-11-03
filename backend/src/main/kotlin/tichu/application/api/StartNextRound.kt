package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Round
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface StartNextRound {
    /**
     * Draws and saves the next round of the active tournament owned by [owner], paired
     * from the current Standings. Fails typed (no side effects) as not-found for a
     * foreign or unknown tournament, as [tichu.vocabulary.TournamentNotActiveException]
     * unless active, as [tichu.vocabulary.RoundIncompleteException] while a table of
     * the current round is not completed, and as
     * [tichu.vocabulary.TotalRoundsReachedException] once the Total Rounds are reached.
     */
    fun start(tournamentId: TournamentId, owner: AdminUserId): Round
}
