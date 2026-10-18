import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: {
        register: vi.fn(),
        login: vi.fn(),
        refresh: vi.fn(),
        logout: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);

describe('app routing', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        auth.refresh.mockRejectedValue(
            new ApiError({
                code: 'UNAUTHENTICATED',
                status: 401,
                field: null,
                message: 'no session',
            }),
        );
    });

    it.each([
        ['/team', 'Teambereich'],
        ['/public', 'Öffentlicher Bereich'],
        ['/login', 'Anmelden'],
        ['/pending-approval', 'Warte auf Freigabe'],
    ])('renders %s with its German heading', async (path, heading) => {
        renderApp(path);

        expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    });

    it('redirects the root path to the public area', async () => {
        renderApp('/');

        expect(
            await screen.findByRole('heading', { name: 'Öffentlicher Bereich' }),
        ).toBeInTheDocument();
    });

    it('redirects unauthenticated visitors from /admin to login', async () => {
        renderApp('/admin');

        expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
    });
});
