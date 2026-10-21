import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';

const sessionDto = {
    teamId: 'team-1',
    tournamentId: 't-1',
    expiresAt: '2026-01-01T10:00:00Z',
};

describe('TeamAuthRepository exchange', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('exchanges a raw token and maps the session to the domain type', async () => {
        mock.onPost('/api/team/auth/exchange').reply(200, sessionDto);

        const session = await TeamAuthRepository.exchange('raw-secret-token');

        expect(mock.history[0]?.data).toBe(JSON.stringify({ token: 'raw-secret-token' }));
        expect(session.teamId).toBe('team-1');
        expect(session.tournamentId).toBe('t-1');
        expect(session.expiresAt).toEqual(new Date('2026-01-01T10:00:00Z'));
    });

    it('rejects a malformed exchange payload at the boundary', async () => {
        mock.onPost('/api/team/auth/exchange').reply(200, { teamId: 'team-1' });

        await expect(TeamAuthRepository.exchange('raw-secret-token')).rejects.toThrow();
    });

    it('surfaces TEAM_ACCESS_INVALID as a typed ApiError for any rejected link', async () => {
        // Unknown, revoked, expired and rate-limited all arrive as this one code (ADR 0001).
        mock.onPost('/api/team/auth/exchange').reply(401, {
            code: 'TEAM_ACCESS_INVALID',
            title: 'Unauthorized',
            status: 401,
        });

        const error = await TeamAuthRepository.exchange('bad-token').catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error.code).toBe('TEAM_ACCESS_INVALID');
        expect(error.status).toBe(401);
    });
});
