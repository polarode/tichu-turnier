import { describe, expect, it } from 'vitest';

import type { Team } from '@/domain/team.domain.ts';
import type { TableOverview } from '@/domain/round.domain.ts';
import { correctionTable } from '@/features/tournaments/correction-table.mapper.ts';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

function team(id: string, name: string): Team {
    return {
        id,
        tournamentId: 't-1',
        name,
        players: [
            { id: `${id}-p1`, teamId: id, name: `${name} 1` },
            { id: `${id}-p2`, teamId: id, name: `${name} 2` },
        ],
    };
}

function table(teamIds: TableOverview['teamIds'], trio = false): TableOverview {
    return {
        tableNumber: 3,
        teamIds,
        trio,
        confirmation: { status: TableStatus.COMPLETED, confirmedTeamIds: [...teamIds] },
        gameCount: trio ? 6 : 4,
        schedule: [{ gameNumber: 1, teamIds: [teamIds[0], teamIds[1]] }],
        games: [],
    };
}

const bonuses = { grandTichuBonus: 200 as const, doubleWinBonus: 100 as const };

describe('correctionTable', () => {
    it('keeps the table seat order with each seated team and its roster', () => {
        const teams = [team('team-a', 'Alpha'), team('team-b', 'Beta')];

        const result = correctionTable({
            tournamentId: 't-1',
            roundNumber: 2,
            table: table(['team-b', 'team-a']),
            teams,
            bonuses,
        });

        expect(result?.teams).toEqual([
            {
                teamId: 'team-b',
                teamName: 'Beta',
                players: [
                    { playerId: 'team-b-p1', name: 'Beta 1' },
                    { playerId: 'team-b-p2', name: 'Beta 2' },
                ],
            },
            {
                teamId: 'team-a',
                teamName: 'Alpha',
                players: [
                    { playerId: 'team-a-p1', name: 'Alpha 1' },
                    { playerId: 'team-a-p2', name: 'Alpha 2' },
                ],
            },
        ]);
    });

    it('takes the bonuses from the tournament settings and addresses the table', () => {
        const result = correctionTable({
            tournamentId: 't-1',
            roundNumber: 2,
            table: table(['team-a', 'team-b']),
            teams: [team('team-a', 'Alpha'), team('team-b', 'Beta')],
            bonuses,
        });

        expect(result).toMatchObject({
            tournamentId: 't-1',
            roundNumber: 2,
            tableNumber: 3,
            grandTichuBonus: 200,
            doubleWinBonus: 100,
            trio: false,
        });
    });

    it('builds the three-team seating of a trio table', () => {
        const result = correctionTable({
            tournamentId: 't-1',
            roundNumber: 1,
            table: table(['team-a', 'team-b', 'team-c'], true),
            teams: [team('team-a', 'Alpha'), team('team-b', 'Beta'), team('team-c', 'Gamma')],
            bonuses,
        });

        expect(result?.teams.map((seated) => seated.teamName)).toEqual(['Alpha', 'Beta', 'Gamma']);
        expect(result?.trio).toBe(true);
    });

    it('gives undefined when a seated team is missing from the teams', () => {
        const result = correctionTable({
            tournamentId: 't-1',
            roundNumber: 1,
            table: table(['team-a', 'team-gone']),
            teams: [team('team-a', 'Alpha'), team('team-b', 'Beta')],
            bonuses,
        });

        expect(result).toBeUndefined();
    });
});
