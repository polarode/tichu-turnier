import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';
import type { TeamWriteInput } from '@/domain/team.domain.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';

const teamDto = {
    id: 'team-1',
    tournamentId: 't-1',
    name: 'The Aces',
    players: [
        { id: 'p-1', teamId: 'team-1', name: 'Alice' },
        { id: 'p-2', teamId: 'team-1', name: 'Bob' },
    ],
};

const writeRequest: TeamWriteInput = {
    name: 'The Aces',
    playerOneName: 'Alice',
    playerTwoName: 'Bob',
};

describe('TeamRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('lists a tournament’s teams and maps them to domain', async () => {
        mock.onGet('/api/tournaments/t-1/teams').reply(200, [teamDto]);

        const teams = await TeamRepository.list('t-1');

        expect(teams).toHaveLength(1);
        expect(teams[0]!.players[1]!.name).toBe('Bob');
    });

    it('gets a single team', async () => {
        mock.onGet('/api/tournaments/t-1/teams/team-1').reply(200, teamDto);

        const team = await TeamRepository.get('t-1', 'team-1');

        expect(team.name).toBe('The Aces');
        expect(team.players).toHaveLength(2);
    });

    it('creates a team with the write payload', async () => {
        mock.onPost('/api/tournaments/t-1/teams').reply(201, teamDto);

        const team = await TeamRepository.create('t-1', writeRequest);

        expect(team.id).toBe('team-1');
        expect(JSON.parse(mock.history.post[0]!.data)).toEqual(writeRequest);
    });

    it('updates a team with the write payload', async () => {
        mock.onPut('/api/tournaments/t-1/teams/team-1').reply(200, teamDto);

        const team = await TeamRepository.update('t-1', 'team-1', writeRequest);

        expect(team.id).toBe('team-1');
        expect(JSON.parse(mock.history.put[0]!.data)).toEqual(writeRequest);
    });

    it('deletes a team', async () => {
        mock.onDelete('/api/tournaments/t-1/teams/team-1').reply(204);

        await expect(TeamRepository.remove('t-1', 'team-1')).resolves.toBeUndefined();
    });

    it('surfaces the field anchor on a validation conflict', async () => {
        mock.onPost('/api/tournaments/t-1/teams').reply(409, {
            status: 409,
            code: 'DUPLICATE_TEAM_NAME',
            detail: 'name taken',
            field: 'name',
        });

        const error = await TeamRepository.create('t-1', writeRequest).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('DUPLICATE_TEAM_NAME');
        expect((error as ApiError).field).toBe('name');
    });

    it('rejects a team without exactly two players at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1/teams/team-1').reply(200, {
            id: 'team-1',
            tournamentId: 't-1',
            name: 'The Aces',
            players: [{ id: 'p-1', teamId: 'team-1', name: 'Alice' }],
        });

        await expect(TeamRepository.get('t-1', 'team-1')).rejects.toThrow();
    });
});
