import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

const dto = {
    id: 't-1',
    name: 'Spring Classic',
    description: 'annual invitational',
    maxTeams: 16,
    settings: { allowGrandTichu: true },
    status: TournamentStatus.SETUP,
    createdAt: '2026-01-01T00:00:00.000Z',
    updatedAt: '2026-01-01T00:00:00.000Z',
};

const startedResponse = {
    tournament: { ...dto, status: TournamentStatus.ACTIVE },
    round: {
        roundNumber: 1,
        tables: [
            { tableNumber: 1, homeTeamId: 'team-a', awayTeamId: 'team-b' },
            { tableNumber: 2, homeTeamId: 'team-c', awayTeamId: 'team-d' },
        ],
    },
};

const roundsResponse = [
    {
        roundNumber: 1,
        tables: [{ tableNumber: 1, homeTeamId: 'team-a', awayTeamId: 'team-b' }],
    },
];

describe('TournamentRepository start/rounds', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('starts a tournament and maps the started tournament plus round 1 to domain', async () => {
        mock.onPost('/api/tournaments/t-1/start').reply(200, startedResponse);

        const { tournament, round } = await TournamentRepository.start('t-1');

        expect(tournament.status).toBe(TournamentStatus.ACTIVE);
        expect(round.number).toBe(1);
        expect(round.tables).toEqual([
            { tableNumber: 1, homeTeamId: 'team-a', awayTeamId: 'team-b' },
            { tableNumber: 2, homeTeamId: 'team-c', awayTeamId: 'team-d' },
        ]);
    });

    it('surfaces start-rejection codes as a typed ApiError', async () => {
        for (const code of [
            'TOURNAMENT_NOT_STARTABLE',
            'INSUFFICIENT_TEAMS',
            'PAIRING_UNSUPPORTED',
        ] as const) {
            mock.onPost('/api/tournaments/t-1/start').replyOnce(409, {
                status: 409,
                code,
                detail: 'rejected',
            });

            const error = await TournamentRepository.start('t-1').catch((e: unknown) => e);

            expect(error).toBeInstanceOf(ApiError);
            expect((error as ApiError).code).toBe(code);
        }
    });

    it('lists rounds of a tournament mapped to domain', async () => {
        mock.onGet('/api/tournaments/t-1/rounds').reply(200, roundsResponse);

        const rounds = await TournamentRepository.rounds('t-1');

        expect(rounds).toEqual([
            {
                number: 1,
                tables: [{ tableNumber: 1, homeTeamId: 'team-a', awayTeamId: 'team-b' }],
            },
        ]);
    });

    it('rejects a malformed rounds response at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1/rounds').reply(200, [{ roundNumber: 'one' }]);

        await expect(TournamentRepository.rounds('t-1')).rejects.toThrow();
    });
});
