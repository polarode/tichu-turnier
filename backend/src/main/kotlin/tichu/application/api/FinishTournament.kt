package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface FinishTournament {
    /**
     * Finishes the active tournament owned by [owner], moving it to `completed`.
     * Fails typed (no side effects) as not-found for a foreign or unknown tournament,
     * as [tichu.vocabulary.TournamentNotActiveException] unless active, as
     * [tichu.vocabulary.RoundsRemainingException] while fewer rounds than the Total
     * Rounds are played, and as [tichu.vocabulary.RoundIncompleteException] while a
     * table of the last round is not completed.
     */
    fun finish(tournamentId: TournamentId, owner: AdminUserId): Tournament
}
