import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AdminRepository } from '@/admin-api/admin.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';

const accountDto = {
    id: 'admin-1',
    email: 'organizer@example.com',
    status: 'PENDING',
    role: 'ADMIN',
    createdAt: '2026-01-01T00:00:00Z',
    approvedBy: null,
    approvedAt: null,
    invitedBy: null,
    invitedAt: null,
};

const inviteDto = {
    id: 'invite-1',
    email: 'invitee@example.com',
    status: 'PENDING',
    createdBy: 'owner-1',
    createdAt: '2026-01-01T00:00:00Z',
    expiresAt: '2026-01-08T00:00:00Z',
    spentAt: null,
};

describe('AdminRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('lists admin accounts and maps them to domain', async () => {
        mock.onGet('/api/admins').reply(200, [accountDto]);

        const accounts = await AdminRepository.listAccounts();

        expect(accounts).toHaveLength(1);
        expect(accounts[0]!.role).toBe('ADMIN');
        expect(accounts[0]!.createdAt).toEqual(new Date('2026-01-01T00:00:00Z'));
    });

    it('approves an account and returns the updated account', async () => {
        mock.onPost('/api/admins/admin-1/approve').reply(200, {
            ...accountDto,
            status: 'ACTIVE',
            approvedBy: 'owner-1',
            approvedAt: '2026-01-02T00:00:00Z',
        });

        const account = await AdminRepository.approve('admin-1');

        expect(account.status).toBe('ACTIVE');
        expect(account.approvedBy).toBe('owner-1');
    });

    it('rejects an account without a response body', async () => {
        mock.onPost('/api/admins/admin-1/reject').reply(204);

        await expect(AdminRepository.reject('admin-1')).resolves.toBeUndefined();
    });

    it('disables and reactivates an account', async () => {
        mock.onPost('/api/admins/admin-1/disable').reply(200, { ...accountDto, status: 'DISABLED' });
        mock.onPost('/api/admins/admin-1/reactivate').reply(200, { ...accountDto, status: 'ACTIVE' });

        await expect(AdminRepository.disable('admin-1')).resolves.toMatchObject({
            status: 'DISABLED',
        });
        await expect(AdminRepository.reactivate('admin-1')).resolves.toMatchObject({
            status: 'ACTIVE',
        });
    });

    it('lists invites without any token material', async () => {
        mock.onGet('/api/invites').reply(200, [inviteDto]);

        const invites = await AdminRepository.listInvites();

        expect(invites).toHaveLength(1);
        expect(invites[0]).not.toHaveProperty('token');
    });

    it('issues an invite with the email and returns the one-time link', async () => {
        mock.onPost('/api/invites').reply(201, {
            ...inviteDto,
            token: 'raw-token',
            link: 'http://localhost:3000/register?invite=raw-token',
        });

        const issued = await AdminRepository.createInvite('invitee@example.com');

        expect(JSON.parse(mock.history.post[0]!.data)).toEqual({ email: 'invitee@example.com' });
        expect(issued.token).toBe('raw-token');
        expect(issued.link).toContain('raw-token');
    });

    it('revokes an invite and returns its updated status', async () => {
        mock.onPost('/api/invites/invite-1/revoke').reply(200, {
            ...inviteDto,
            status: 'REVOKED',
        });

        const invite = await AdminRepository.revokeInvite('invite-1');

        expect(invite.status).toBe('REVOKED');
    });

    it('surfaces a forbidden response as a typed ApiError', async () => {
        mock.onGet('/api/admins').reply(403, {
            status: 403,
            code: 'FORBIDDEN',
            detail: 'Access denied',
        });

        const error = await AdminRepository.listAccounts().catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('FORBIDDEN');
    });

    it('rejects a malformed account payload at the zod boundary', async () => {
        mock.onGet('/api/admins').reply(200, [{ id: 'admin-1', email: null }]);

        await expect(AdminRepository.listAccounts()).rejects.toThrow();
    });
});
