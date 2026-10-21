import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';

const revealedDto = {
    teamId: 'team-1',
    tournamentId: 't-1',
    token: 'raw-secret-token',
    link: 'https://tichu.example/team/login?token=raw-secret-token',
};

describe('TeamRepository access link (reveal, rotate, revoke)', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('reveals the access link and maps it to the domain type', async () => {
        mock.onPost('/api/tournaments/t-1/teams/team-1/access-link/reveal').reply(200, revealedDto);

        const revealed = await TeamRepository.revealAccessLink('t-1', 'team-1');

        expect(revealed.token).toBe('raw-secret-token');
        expect(revealed.link).toBe('https://tichu.example/team/login?token=raw-secret-token');
    });

    it('rejects a malformed reveal payload at the boundary', async () => {
        mock
            .onPost('/api/tournaments/t-1/teams/team-1/access-link/reveal')
            .reply(200, { teamId: 'team-1' });

        await expect(TeamRepository.revealAccessLink('t-1', 'team-1')).rejects.toThrow();
    });

    it('surfaces LINK_ALREADY_REVEALED as a typed ApiError', async () => {
        mock.onPost('/api/tournaments/t-1/teams/team-1/access-link/reveal').reply(409, {
            code: 'LINK_ALREADY_REVEALED',
            title: 'Conflict',
            status: 409,
        });

        const error = await TeamRepository.revealAccessLink('t-1', 'team-1').catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error.code).toBe('LINK_ALREADY_REVEALED');
    });

    it('rotates the access link without a response body', async () => {
        mock.onPost('/api/tournaments/t-1/teams/team-1/access-link/rotate').reply(204);

        await expect(TeamRepository.rotateAccessLink('t-1', 'team-1')).resolves.toBeUndefined();
    });

    it('revokes the access link without a response body', async () => {
        mock.onPost('/api/tournaments/t-1/teams/team-1/access-link/revoke').reply(204);

        await expect(TeamRepository.revokeAccessLink('t-1', 'team-1')).resolves.toBeUndefined();
    });
});
