import { screen } from '@testing-library/react';
import {
    createMemoryHistory,
    createRootRoute,
    createRouter,
    RouterProvider,
} from '@tanstack/react-router';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { SignInPopover } from '@/features/auth/components/sign-in-popover.component.tsx';
import { ApiError } from '@/shared/api/api-error.ts';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: {
        register: vi.fn(),
        login: vi.fn(),
        refresh: vi.fn(),
        logout: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);

/** The popover alone on a bare route (the login form needs a router), next to something to click away to. */
function renderPopover() {
    const rootRoute = createRootRoute({
        component: () => (
            <>
                <SignInPopover />
                <button type="button">outside</button>
            </>
        ),
    });
    const router = createRouter({
        routeTree: rootRoute,
        history: createMemoryHistory({ initialEntries: ['/'] }),
    });
    return renderWithProviders(<RouterProvider router={router} />);
}

describe('SignInPopover', () => {
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

    it('moves focus into the form when it opens', async () => {
        renderPopover();

        await userEvent.click(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        );

        expect(screen.getByLabelText('E-Mail')).toHaveFocus();
    });

    it('closes on Escape and returns focus to the icon', async () => {
        renderPopover();
        const trigger = await screen.findByRole('button', { name: 'Als Organisator anmelden' });
        await userEvent.click(trigger);

        await userEvent.keyboard('{Escape}');

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(trigger).toHaveAttribute('aria-expanded', 'false');
        expect(trigger).toHaveFocus();
    });

    it('closes when the visitor clicks elsewhere', async () => {
        renderPopover();
        await userEvent.click(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        );

        await userEvent.click(screen.getByRole('button', { name: 'outside' }));

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('closes when focus moves past it', async () => {
        renderPopover();
        await userEvent.click(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        );
        screen.getByRole('link', { name: 'Jetzt erstellen' }).focus();

        await userEvent.tab();

        expect(screen.getByRole('button', { name: 'outside' })).toHaveFocus();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
});
