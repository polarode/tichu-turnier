import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';

const adminDto = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' };

describe('AuthRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('registers and maps the response to the domain', async () => {
        mock.onPost('/api/auth/register').reply(201, adminDto);

        const admin = await AuthRepository.register({
            email: 'organizer@example.com',
            password: 's3cret-pw',
        });

        expect(admin).toEqual(adminDto);
        expect(JSON.parse(mock.history.post[0]!.data)).toEqual({
            email: 'organizer@example.com',
            password: 's3cret-pw',
        });
    });

    it('includes the invite token when registering from an invite link', async () => {
        mock.onPost('/api/auth/register').reply(201, adminDto);

        await AuthRepository.register({
            email: 'invitee@example.com',
            password: 's3cret-pw',
            inviteToken: 'raw-invite-token',
        });

        expect(JSON.parse(mock.history.post[0]!.data)).toEqual({
            email: 'invitee@example.com',
            password: 's3cret-pw',
            inviteToken: 'raw-invite-token',
        });
    });

    it('never sends an invite field for plain registration', async () => {
        mock.onPost('/api/auth/register').reply(201, adminDto);

        await AuthRepository.register({
            email: 'organizer@example.com',
            password: 's3cret-pw',
        });

        expect(JSON.parse(mock.history.post[0]!.data)).not.toHaveProperty('inviteToken');
    });

    it('surfaces an invite error code as a typed ApiError', async () => {
        mock.onPost('/api/auth/register').reply(409, {
            status: 409,
            code: 'INVITE_EXPIRED',
            detail: 'this invite has expired',
        });

        const error = await AuthRepository.register({
            email: 'invitee@example.com',
            password: 's3cret-pw',
            inviteToken: 'stale',
        }).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('INVITE_EXPIRED');
    });

    it('logs in and maps the response to the domain', async () => {
        mock.onPost('/api/auth/login').reply(200, adminDto);

        const admin = await AuthRepository.login({
            email: 'organizer@example.com',
            password: 's3cret-pw',
        });

        expect(admin).toEqual(adminDto);
    });

    it('refreshes the session and returns the current admin', async () => {
        mock.onPost('/api/auth/refresh').reply(200, adminDto);

        await expect(AuthRepository.refresh()).resolves.toEqual(adminDto);
    });

    it('logs out without a response body', async () => {
        mock.onPost('/api/auth/logout').reply(204);

        await expect(AuthRepository.logout()).resolves.toBeUndefined();
    });

    it('surfaces the backend error code as a typed ApiError', async () => {
        mock.onPost('/api/auth/login').reply(401, {
            status: 401,
            code: 'INVALID_CREDENTIALS',
            detail: 'email or password is wrong',
        });

        const error = await AuthRepository.login({
            email: 'organizer@example.com',
            password: 'wrong',
        }).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('INVALID_CREDENTIALS');
    });

    it('rejects a malformed response at the zod boundary', async () => {
        mock.onPost('/api/auth/login').reply(200, { id: 1, email: null });

        await expect(
            AuthRepository.login({ email: 'organizer@example.com', password: 's3cret-pw' }),
        ).rejects.toThrow();
    });
});
