import type { SeatedTeams, TeamTable, TeamTableTeam } from '@/domain/game.domain.ts';
import type { TableOverview } from '@/domain/round.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { ScoringBonuses } from '@/domain/tournament.domain.ts';

/**
 * The table as the game entry form needs it, built from the admin's view of a round
 * table: its bonuses from the tournament settings, its seated teams with their
 * rosters in seat order, and the table's status, schedule and trio flag. Undefined
 * when a seated team is missing from [teams], since the form cannot show a game
 * without its players.
 */
export function correctionTable({
    tournamentId,
    roundNumber,
    table,
    teams,
    bonuses,
}: {
    tournamentId: string;
    roundNumber: number;
    table: TableOverview;
    teams: readonly Team[];
    bonuses: ScoringBonuses;
}): TeamTable | undefined {
    const seated = table.teamIds.flatMap((teamId) => {
        const team = teams.find((candidate) => candidate.id === teamId);
        return team ? [toTableTeam(team)] : [];
    });
    const seatedTeams = seatOf(seated);
    if (!seatedTeams) return undefined;

    return {
        tournamentId,
        roundNumber,
        tableNumber: table.tableNumber,
        confirmation: table.confirmation,
        grandTichuBonus: bonuses.grandTichuBonus,
        doubleWinBonus: bonuses.doubleWinBonus,
        teams: seatedTeams,
        trio: table.trio,
        schedule: table.schedule,
    };
}

/** A team with its two players (the domain tuple guarantees exactly two), as the form reads them. */
function toTableTeam(team: Team): TeamTableTeam {
    const [first, second] = team.players;
    return {
        teamId: team.id,
        teamName: team.name,
        players: [
            { playerId: first.id, name: first.name },
            { playerId: second.id, name: second.name },
        ],
    };
}

/** The seated teams as the table tuple: two, or three at the trio table; anything else is no table. */
function seatOf(seated: readonly TeamTableTeam[]): SeatedTeams<TeamTableTeam> | undefined {
    const [first, second, third, ...rest] = seated;
    if (!first || !second || rest.length > 0) return undefined;
    return third ? [first, second, third] : [first, second];
}
