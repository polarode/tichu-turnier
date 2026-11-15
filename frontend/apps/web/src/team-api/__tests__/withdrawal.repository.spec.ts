import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';

const withdrawalDto = {
    reason: 'DISQUALIFIED',
    compensation: 'FULL',
    roundNumber: 2,
    withdrawnAt: '2026-11-14T18:30:00Z',
};

const withdrawalPath = '/api/tournaments/t-1/teams/team-1/withdrawal';

describe('TeamRepository withdrawal (withdraw, undo)', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('withdraws the team with the reason and compensation and maps the withdrawal', async () => {
        mock.onPost(withdrawalPath).reply(200, withdrawalDto);

        const withdrawal = await TeamRepository.withdraw('t-1', 'team-1', {
            reason: 'DISQUALIFIED',
            compensation: 'FULL',
        });

        expect(JSON.parse(mock.history.post[0]!.data)).toEqual({
            reason: 'DISQUALIFIED',
            compensation: 'FULL',
        });
        expect(withdrawal).toEqual({
            reason: 'DISQUALIFIED',
            compensation: 'FULL',
            roundNumber: 2,
            withdrawnAt: new Date('2026-11-14T18:30:00Z'),
        });
    });

    it('rejects a malformed withdrawal payload at the boundary', async () => {
        mock.onPost(withdrawalPath).reply(200, { reason: 'DISQUALIFIED' });

        await expect(
            TeamRepository.withdraw('t-1', 'team-1', {
                reason: 'DISQUALIFIED',
                compensation: 'NONE',
            }),
        ).rejects.toThrow();
    });

    it('surfaces TEAM_ALREADY_WITHDRAWN as a typed ApiError', async () => {
        mock.onPost(withdrawalPath).reply(409, {
            code: 'TEAM_ALREADY_WITHDRAWN',
            title: 'Conflict',
            status: 409,
        });

        const error = await TeamRepository.withdraw('t-1', 'team-1', {
            reason: 'VOLUNTARY',
            compensation: 'HALF',
        }).catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error.code).toBe('TEAM_ALREADY_WITHDRAWN');
    });

    it('undoes the withdrawal without a response body', async () => {
        mock.onDelete(withdrawalPath).reply(204);

        await expect(TeamRepository.undoWithdrawal('t-1', 'team-1')).resolves.toBeUndefined();
    });

    it('surfaces WITHDRAWAL_FINAL as a typed ApiError when undo is refused', async () => {
        mock.onDelete(withdrawalPath).reply(409, {
            code: 'WITHDRAWAL_FINAL',
            title: 'Conflict',
            status: 409,
        });

        const error = await TeamRepository.undoWithdrawal('t-1', 'team-1').catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error.code).toBe('WITHDRAWAL_FINAL');
    });
});
