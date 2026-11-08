package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Highlight
import tichu.domain.TeamStatistics
import tichu.domain.TeamTrend
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface GetStatistics {
    /**
     * The statistics of the tournament owned by [owner] — admin-only until the results
     * are published. Fails as not-found when the tournament does not exist or is owned
     * by another admin, and as [tichu.vocabulary.TournamentNotStartedException] while it
     * is still in setup.
     */
    fun get(tournamentId: TournamentId, owner: AdminUserId): TournamentStatistics
}

/** The statistics read model: every team's Team Statistics, the Highlights and the Team Trends. */
data class TournamentStatistics(
    val teams: List<TeamStatisticsEntry>,
    val highlights: List<Highlight>,
    val trends: List<TeamTrend>,
)

/** One team's Team Statistics together with the team's name, which the screens show. */
data class TeamStatisticsEntry(
    val teamName: String,
    val statistics: TeamStatistics,
)
