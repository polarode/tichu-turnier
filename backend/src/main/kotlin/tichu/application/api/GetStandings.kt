package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Standings
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface GetStandings {
    /**
     * The current Standings of the tournament owned by [owner] — admin-only: teams
     * and the public never see standings before the results are published.
     * Fails as not-found when the tournament does not exist or is owned by another admin,
     * and as [tichu.vocabulary.TournamentNotStartedException] while it is still in setup.
     */
    fun get(tournamentId: TournamentId, owner: AdminUserId): Standings
}
