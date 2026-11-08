import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { apiClient } from '@/shared/api/api-client.ts';
import { StatisticsRepository } from '@/statistics-api/statistics.repository.ts';

/** The shared contract fixture's wire body, served by all three statistics endpoints alike. */
const body = (
    JSON.parse(
        readFileSync(
            resolve(__dirname, '../../../../../../contract-fixtures/statistics.json'),
            'utf8',
        ),
    ) as { response: unknown }
).response;

describe('StatisticsRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('reads the organizer statistics of a tournament and maps them to domain', async () => {
        mock.onGet('/api/tournaments/t-1/statistics').reply(200, body);

        const statistics = await StatisticsRepository.organizer('t-1');

        expect(statistics.teams[1]).toEqual({
            teamId: 'team-falcons',
            teamName: 'Falcons',
            successfulSmallTichu: 1,
            successfulGrandTichu: 1,
            bombCount: 4,
            bombsPerGame: 0.5,
            doubleWins: 1,
            tichuCalls: 2,
            destroyedTichus: 0,
            beschissGames: 1,
            goodHorseRatio: 93.33333333333333,
        });
        expect(statistics.trends[1]!.points).toEqual([
            { roundNumber: 1, victoryPoints: 6 },
            { roundNumber: 2, victoryPoints: 9 },
        ]);
    });

    it('reads the published statistics of a public tournament and maps them to domain', async () => {
        mock.onGet('/api/public/tournaments/t-1/statistics').reply(200, body);

        const statistics = await StatisticsRepository.publicStatistics('t-1');

        expect(statistics.teams[4]).toEqual({
            teamId: 'team-wolves',
            teamName: 'Wolves',
            successfulSmallTichu: 0,
            successfulGrandTichu: 0,
            bombCount: 0,
            bombsPerGame: null,
            doubleWins: 0,
            tichuCalls: 0,
            destroyedTichus: 0,
            beschissGames: 0,
            goodHorseRatio: null,
        });
    });

    it('rejects a malformed statistics body at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1/statistics').reply(200, {
            teams: 'nope',
            highlights: [],
            trends: [],
        });

        await expect(StatisticsRepository.organizer('t-1')).rejects.toThrow();
    });

    it('rejects an unknown highlight metric at the zod boundary', async () => {
        mock.onGet('/api/public/tournaments/t-1/statistics').reply(200, {
            ...(body as Record<string, unknown>),
            highlights: [{ metric: 'TICHU_FAILS', value: 1, teamIds: ['team-falcons'] }],
        });

        await expect(StatisticsRepository.publicStatistics('t-1')).rejects.toThrow();
    });
});
