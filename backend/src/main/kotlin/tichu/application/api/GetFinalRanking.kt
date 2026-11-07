package tichu.application.api

import tichu.domain.Standings

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface GetFinalRanking {
    /**
     * The Final Ranking of the principal's own tournament, as a team sees it. The
     * tournament comes from the session only. Fails as [tichu.vocabulary.ResultsNotPublishedException]
     * until the organizer has published the results, in setup, active and completed alike.
     */
    fun get(principal: AuthenticatedTeam): Standings
}
