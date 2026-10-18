import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiError, UNKNOWN_ERROR_CODE } from '@/shared/api/api-error.ts';
import { apiClient, resolveApiUrl } from '@/shared/api/api-client.ts';

describe('apiClient', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('sends the httpOnly session cookie', () => {
        expect(apiClient.defaults.withCredentials).toBe(true);
    });

    it('resolves relative paths against the API base', () => {
        expect(resolveApiUrl('/api/tournaments')).toMatch(/^https?:\/\/.+\/api\/tournaments$/);
    });

    it('maps a problem response to a typed ApiError with its code', async () => {
        mock.onPost('/api/auth/login').reply(401, {
            status: 401,
            code: 'INVALID_CREDENTIALS',
            detail: 'email or password is wrong',
        });

        const error = await apiClient.post('/api/auth/login').catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('INVALID_CREDENTIALS');
        expect((error as ApiError).status).toBe(401);
    });

    it('keeps the field anchor from a problem response', async () => {
        mock.onPost('/api/tournaments').reply(409, {
            status: 409,
            code: 'DUPLICATE_TEAM_NAME',
            detail: 'name taken',
            field: 'name',
        });

        const error = await apiClient.post('/api/tournaments').catch((e: unknown) => e);

        expect((error as ApiError).field).toBe('name');
    });

    it('maps an unrecognized code and a non-problem body to UNKNOWN', async () => {
        mock.onGet('/api/unknown-code').reply(500, { code: 'BRAND_NEW' });
        mock.onGet('/api/not-a-problem').reply(502, '<html>bad gateway</html>');

        const first = await apiClient.get('/api/unknown-code').catch((e: unknown) => e);
        const second = await apiClient.get('/api/not-a-problem').catch((e: unknown) => e);

        expect((first as ApiError).code).toBe(UNKNOWN_ERROR_CODE);
        expect((first as ApiError).status).toBe(500);
        expect((second as ApiError).code).toBe(UNKNOWN_ERROR_CODE);
        expect((second as ApiError).status).toBe(502);
    });

    it('returns successful responses untouched', async () => {
        mock.onGet('/api/ping').reply(200, { ok: true });

        const response = await apiClient.get<{ ok: boolean }>('/api/ping');

        expect(response.data).toEqual({ ok: true });
    });
});
