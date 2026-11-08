package tichu.application.usecase

import tichu.application.api.GetStatistics
import tichu.application.api.TeamStatisticsEntry
import tichu.application.api.TournamentStatistics
import tichu.domain.AdminUserId
import tichu.domain.Highlight
import tichu.domain.PlayedRound
import tichu.domain.Team
import tichu.domain.TeamRepository
import tichu.domain.TeamStatistics
import tichu.domain.TeamTrend
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.TournamentNotStartedException

/**
 * The admin's statistics read: loads every round with its games and confirmations and
 * builds the statistics from them with [buildTournamentStatistics]. Owner-scoped; a
 * tournament still in setup has no statistics yet (409).
 */
class GetStatisticsQuery(
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
    private val playedRounds: PlayedRoundsReader,
) : GetStatistics {
    override fun get(tournamentId: TournamentId, owner: AdminUserId): TournamentStatistics {
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, owner)
        if (tournament.status == TournamentStatus.SETUP) {
            throw TournamentNotStartedException("Tournament ${tournamentId.value} has no statistics before it starts")
        }
        return buildTournamentStatistics(
            tournament,
            teamRepository.findAllByTournament(tournamentId),
            playedRounds.of(tournamentId),
        )
    }
}

/**
 * The statistics read model of [tournament]: the Team Statistics of [teams] (ordered by
 * name, each with its name), the Highlights and the Team Trends, all over the completed
 * tables of [rounds] and scored with the tournament's own settings. Shared by every
 * statistics read; each read decides only who may see it and when.
 */
fun buildTournamentStatistics(
    tournament: Tournament,
    teams: List<Team>,
    rounds: List<PlayedRound>,
): TournamentStatistics {
    val byName = teams.sortedBy { it.name }
    val statistics: List<TeamStatistics> = TeamStatistics.compute(byName, rounds, tournament.settings)
    val highlights: List<Highlight> = Highlight.of(statistics)
    val trends: List<TeamTrend> = TeamTrend.compute(byName, rounds, tournament.settings)
    return TournamentStatistics(
        teams = byName.zip(statistics) { team, teamStatistics -> TeamStatisticsEntry(team.name, teamStatistics) },
        highlights = highlights,
        trends = trends,
    )
}
