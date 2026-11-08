import type {
    Highlight,
    StatisticsTeam,
    TeamStatistics,
    TeamTrend,
    TournamentStatistics,
} from '@/domain/statistics.domain.ts';
import type { TournamentStatisticsDto } from '@/statistics-api/dtos/statistics.dto.ts';

/**
 * Pure mapping function from the statistics wire body to the domain. Highlights and trends
 * carry team ids only on the wire, so each is resolved to its name here, against the team
 * list of the same body. Unit-tested in isolation (see `__tests__`).
 */
export function statisticsDtoToDomain(dto: TournamentStatisticsDto): TournamentStatistics {
    const namesById = new Map(dto.teams.map((team) => [team.teamId, team.teamName] as const));

    const resolve = (teamId: string): StatisticsTeam => {
        const teamName = namesById.get(teamId);
        if (teamName === undefined) {
            throw new Error(`statistics refer to team ${teamId}, which is not in its team list`);
        }
        return { teamId, teamName };
    };

    return {
        teams: dto.teams.map((team): TeamStatistics => ({
            teamId: team.teamId,
            teamName: team.teamName,
            successfulSmallTichu: team.successfulSmallTichu,
            successfulGrandTichu: team.successfulGrandTichu,
            bombCount: team.bombCount,
            bombsPerGame: team.bombsPerGame,
            doubleWins: team.doubleWins,
            tichuCalls: team.tichuCalls,
            destroyedTichus: team.destroyedTichus,
            beschissGames: team.beschissGames,
            goodHorseRatio: team.goodHorseRatio,
        })),
        highlights: dto.highlights.map((highlight): Highlight => ({
            metric: highlight.metric,
            value: highlight.value,
            teams: highlight.teamIds.map(resolve),
        })),
        trends: dto.trends.map((trend): TeamTrend => ({
            ...resolve(trend.teamId),
            points: trend.points.map((point) => ({
                roundNumber: point.roundNumber,
                victoryPoints: point.victoryPoints,
            })),
        })),
    };
}
