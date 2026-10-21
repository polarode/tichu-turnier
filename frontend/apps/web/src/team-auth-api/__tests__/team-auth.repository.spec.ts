import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';

describe('TeamAuthRepository me', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('fetches the team overview and maps it to the domain type', async () => {
        mock.onGet('/api/team/me').reply(200, {
            teamId: 'team-1',
            tournamentId: 't-1',
            tournamentName: 'Spring Classic',
            teamName: 'Team Alpha',
            players: [
                { id: 'p-1', teamId: 'team-1', name: 'Alice' },
                { id: 'p-2', teamId: 'team-1', name: 'Bob' },
            ],
        });

        const overview = await TeamAuthRepository.me();

        expect(overview).toEqual({
            teamId: 'team-1',
            tournamentId: 't-1',
            tournamentName: 'Spring Classic',
            teamName: 'Team Alpha',
            players: [
                { id: 'p-1', name: 'Alice' },
                { id: 'p-2', name: 'Bob' },
            ],
        });
    });

    it('rejects a malformed me payload at the boundary', async () => {
        mock.onGet('/api/team/me').reply(200, { teamId: 'team-1' });

        await expect(TeamAuthRepository.me()).rejects.toThrow();
    });

    it('surfaces a 401 as a typed ApiError when the session is gone', async () => {
        mock.onGet('/api/team/me').reply(401, {
            code: 'UNAUTHENTICATED',
            title: 'Unauthorized',
            status: 401,
        });

        const error = await TeamAuthRepository.me().catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error.status).toBe(401);
    });
});
