import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';
import type { TournamentWriteInput } from '@/domain/tournament.domain.ts';
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

const writeRequest: TournamentWriteInput = {
    name: 'Spring Classic',
    description: 'annual invitational',
    maxTeams: 16,
    settings: { allowGrandTichu: true },
};

describe('TournamentRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('lists tournaments and maps them to domain', async () => {
        mock.onGet('/api/tournaments').reply(200, [dto]);

        const tournaments = await TournamentRepository.list();

        expect(tournaments).toHaveLength(1);
        expect(tournaments[0]!.name).toBe('Spring Classic');
        expect(tournaments[0]!.createdAt).toBeInstanceOf(Date);
    });

    it('gets a single tournament', async () => {
        mock.onGet('/api/tournaments/t-1').reply(200, dto);

        const tournament = await TournamentRepository.get('t-1');

        expect(tournament.id).toBe('t-1');
        expect(tournament.status).toBe(TournamentStatus.SETUP);
    });

    it('creates a tournament with the write payload', async () => {
        mock.onPost('/api/tournaments').reply(201, dto);

        const tournament = await TournamentRepository.create(writeRequest);

        expect(tournament.id).toBe('t-1');
        expect(JSON.parse(mock.history.post[0]!.data)).toEqual(writeRequest);
    });

    it('updates a tournament with the write payload', async () => {
        mock.onPut('/api/tournaments/t-1').reply(200, dto);

        const tournament = await TournamentRepository.update('t-1', writeRequest);

        expect(tournament.id).toBe('t-1');
        expect(JSON.parse(mock.history.put[0]!.data)).toEqual(writeRequest);
    });

    it('deletes a tournament', async () => {
        mock.onDelete('/api/tournaments/t-1').reply(204);

        await expect(TournamentRepository.remove('t-1')).resolves.toBeUndefined();
    });

    it('surfaces the backend error code as a typed ApiError', async () => {
        mock.onPut('/api/tournaments/t-1').reply(409, {
            status: 409,
            code: 'TOURNAMENT_NOT_EDITABLE',
            detail: 'tournament has already started',
        });

        const error = await TournamentRepository.update('t-1', writeRequest).catch(
            (e: unknown) => e,
        );

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('TOURNAMENT_NOT_EDITABLE');
    });

    it('rejects a malformed response at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1').reply(200, { id: 't-1', name: 'Missing fields' });

        await expect(TournamentRepository.get('t-1')).rejects.toThrow();
    });
});
