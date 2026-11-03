package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface SetTotalRounds {
    /**
     * Changes the Total Rounds of the active tournament owned by [owner] — the one
     * setting not locked by the start (in setup they are part of the tournament form).
     *
     * Fails as not-found when [id] does not exist or is owned by another admin, as
     * [tichu.vocabulary.TournamentNotEditableException] unless the tournament is
     * active, as [tichu.vocabulary.InvalidFieldException] outside 1–20, and as
     * [tichu.vocabulary.TotalRoundsBelowRoundCountException] below the rounds
     * already drawn.
     */
    fun set(id: TournamentId, totalRounds: Int, owner: AdminUserId): Tournament
}
