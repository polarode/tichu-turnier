import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { statisticsDtoSchema } from '@/statistics-api/dtos/statistics.dto.ts';
import { statisticsDtoToDomain } from '@/statistics-api/mappers/statistics.mapper.ts';

/**
 * The shared contract fixture, read from disk exactly as the backend integration test
 * reads it: the wire body in `response` runs through the real zod schema and mapper.
 */
const statisticsFixture = JSON.parse(
    readFileSync(
        resolve(__dirname, '../../../../../../../contract-fixtures/statistics.json'),
        'utf8',
    ),
) as { response: unknown };

describe('statisticsDtoToDomain for the shared fixture', () => {
    const statistics = statisticsDtoToDomain(statisticsDtoSchema.parse(statisticsFixture.response));

    it('maps each team statistics line with its name and a null bombsPerGame for a team never seated', () => {
        expect(statistics.teams).toEqual([
            {
                teamId: 'team-badgers',
                teamName: 'Badgers',
                successfulSmallTichu: 2,
                successfulGrandTichu: 0,
                bombCount: 3,
                bombsPerGame: 0.375,
                doubleWins: 1,
            },
            {
                teamId: 'team-falcons',
                teamName: 'Falcons',
                successfulSmallTichu: 1,
                successfulGrandTichu: 1,
                bombCount: 4,
                bombsPerGame: 0.5,
                doubleWins: 1,
            },
            {
                teamId: 'team-owls',
                teamName: 'Owls',
                successfulSmallTichu: 1,
                successfulGrandTichu: 1,
                bombCount: 3,
                bombsPerGame: 0.375,
                doubleWins: 0,
            },
            {
                teamId: 'team-ravens',
                teamName: 'Ravens',
                successfulSmallTichu: 1,
                successfulGrandTichu: 1,
                bombCount: 4,
                bombsPerGame: 0.5,
                doubleWins: 0,
            },
            {
                teamId: 'team-wolves',
                teamName: 'Wolves',
                successfulSmallTichu: 0,
                successfulGrandTichu: 0,
                bombCount: 0,
                bombsPerGame: null,
                doubleWins: 0,
            },
        ]);
    });

    it('resolves every highlight team to its id and name', () => {
        expect(statistics.highlights).toEqual([
            {
                metric: 'SUCCESSFUL_TICHU',
                value: 2,
                teams: [
                    { teamId: 'team-badgers', teamName: 'Badgers' },
                    { teamId: 'team-falcons', teamName: 'Falcons' },
                    { teamId: 'team-owls', teamName: 'Owls' },
                    { teamId: 'team-ravens', teamName: 'Ravens' },
                ],
            },
            {
                metric: 'BOMBS',
                value: 4,
                teams: [
                    { teamId: 'team-falcons', teamName: 'Falcons' },
                    { teamId: 'team-ravens', teamName: 'Ravens' },
                ],
            },
            {
                metric: 'DOUBLE_WINS',
                value: 1,
                teams: [
                    { teamId: 'team-badgers', teamName: 'Badgers' },
                    { teamId: 'team-falcons', teamName: 'Falcons' },
                ],
            },
        ]);
    });

    it('gives each trend its team name and cumulative victory points per round, empty for a team never seated', () => {
        expect(statistics.trends).toEqual([
            {
                teamId: 'team-badgers',
                teamName: 'Badgers',
                points: [
                    { roundNumber: 1, victoryPoints: 4 },
                    { roundNumber: 2, victoryPoints: 9 },
                ],
            },
            {
                teamId: 'team-falcons',
                teamName: 'Falcons',
                points: [
                    { roundNumber: 1, victoryPoints: 6 },
                    { roundNumber: 2, victoryPoints: 9 },
                ],
            },
            {
                teamId: 'team-owls',
                teamName: 'Owls',
                points: [
                    { roundNumber: 1, victoryPoints: 3 },
                    { roundNumber: 2, victoryPoints: 7 },
                ],
            },
            {
                teamId: 'team-ravens',
                teamName: 'Ravens',
                points: [
                    { roundNumber: 1, victoryPoints: 4 },
                    { roundNumber: 2, victoryPoints: 9 },
                ],
            },
            { teamId: 'team-wolves', teamName: 'Wolves', points: [] },
        ]);
    });

    it('rejects a highlight that names a team missing from the team list', () => {
        const broken = {
            ...(statisticsFixture.response as Record<string, unknown>),
            highlights: [{ metric: 'BOMBS', value: 4, teamIds: ['team-ghosts'] }],
        };

        expect(() => statisticsDtoToDomain(statisticsDtoSchema.parse(broken))).toThrow(
            /team-ghosts/,
        );
    });
});
