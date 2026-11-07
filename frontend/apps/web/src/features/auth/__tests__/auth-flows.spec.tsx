import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { sessionQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: {
        register: vi.fn(),
        login: vi.fn(),
        refresh: vi.fn(),
        logout: vi.fn(),
    },
}));

vi.mock('@/tournament-api/tournament.repository.ts', async () => {
    const { mockTournamentRepositoryModule } =
        await import('@/test-helpers/tournament-repository.mock.ts');
    return mockTournamentRepositoryModule({ list: vi.fn().mockResolvedValue([]) });
});

const auth = vi.mocked(AuthRepository);
const tournamentRepo = vi.mocked(TournamentRepository);
const admin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

const noSession = () =>
    new ApiError({ code: 'UNAUTHENTICATED', status: 401, field: null, message: 'no session' });

describe('admin auth flows', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        auth.refresh.mockRejectedValue(noSession());
        // The admin dashboard fetches tournaments as soon as it mounts.
        tournamentRepo.list.mockResolvedValue([]);
        // The public area (landed on after sign-out) fetches the public tournaments.
        tournamentRepo.listPublicTournaments.mockResolvedValue([]);
    });

    it('validates the login form before submitting', async () => {
        renderApp('/login');

        await userEvent.click(await screen.findByRole('button', { name: 'Anmelden' }));

        expect(await screen.findByText('E-Mail ist erforderlich.')).toBeInTheDocument();
        expect(screen.getByText('Passwort ist erforderlich.')).toBeInTheDocument();
        expect(auth.login).not.toHaveBeenCalled();
    });

    it('shows a localized message for invalid credentials', async () => {
        auth.login.mockRejectedValue(
            new ApiError({
                code: 'INVALID_CREDENTIALS',
                status: 401,
                field: null,
                message: 'nope',
            }),
        );
        renderApp('/login');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'wrong-password');
        await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }));

        expect(await screen.findByText('E-Mail oder Passwort ist falsch.')).toBeInTheDocument();
    });

    it('signs in and lands in the admin area', async () => {
        auth.login.mockResolvedValue(admin);
        renderApp('/login');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'correct-password');
        await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }));

        // The shell shows an avatar once signed in; email and logout live in its menu.
        await userEvent.click(await screen.findByRole('button', { name: 'Konto' }));
        expect(screen.getByText('organizer@example.com')).toBeInTheDocument();
        expect(screen.getByRole('menuitem', { name: 'Abmelden' })).toBeInTheDocument();
    });

    it('validates the register form password length', async () => {
        renderApp('/register');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'short');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByText('Das Passwort muss mindestens 8 Zeichen lang sein.'),
        ).toBeInTheDocument();
        expect(auth.register).not.toHaveBeenCalled();
    });

    it('anchors a duplicate-email error to the email field', async () => {
        auth.register.mockRejectedValue(
            new ApiError({
                code: 'EMAIL_ALREADY_REGISTERED',
                status: 409,
                field: null,
                message: 'dup',
            }),
        );
        renderApp('/register');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByText('Es existiert bereits ein Konto mit dieser E-Mail-Adresse.'),
        ).toBeInTheDocument();
        expect(screen.getByLabelText('E-Mail')).toHaveAttribute('aria-invalid', 'true');
    });

    it('registers a pending account and lands on the awaiting-approval state', async () => {
        auth.register.mockResolvedValue(admin);
        renderApp('/register');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByRole('heading', { name: 'Warte auf Freigabe' }),
        ).toBeInTheDocument();
        // A pending account gets no session, so registration must not attempt a login.
        expect(auth.login).not.toHaveBeenCalled();
    });

    it('activates an invited registration and signs the organizer straight in', async () => {
        auth.register.mockResolvedValue(admin);
        auth.login.mockResolvedValue(admin);
        renderApp('/register?invite=invite-token');

        expect(
            await screen.findByText(
                'Du wurdest eingeladen. Schließe die Registrierung ab, um dein Konto sofort zu aktivieren.',
            ),
        ).toBeInTheDocument();

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'invitee@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(await screen.findByRole('button', { name: 'Konto' })).toBeInTheDocument();
        expect(auth.register).toHaveBeenCalledWith(
            expect.objectContaining({ inviteToken: 'invite-token' }),
        );
        expect(auth.login).toHaveBeenCalled();
    });

    it('points an invited organizer at sign-in when the follow-up login fails', async () => {
        auth.register.mockResolvedValue(admin);
        auth.login.mockRejectedValue(
            new ApiError({
                code: 'INVALID_CREDENTIALS',
                status: 401,
                field: null,
                message: 'nope',
            }),
        );
        renderApp('/register?invite=invite-token');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'invitee@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByText('Dein Konto ist aktiv. Bitte melde dich an.'),
        ).toBeInTheDocument();
        // The account is active, so the form stays put with a sign-in prompt instead of
        // implying registration failed.
        expect(screen.getByRole('button', { name: 'Konto erstellen' })).toBeInTheDocument();
    });

    it('shows a localized error when an invite link has expired', async () => {
        auth.register.mockRejectedValue(
            new ApiError({
                code: 'INVITE_EXPIRED',
                status: 409,
                field: null,
                message: 'expired',
            }),
        );
        renderApp('/register?invite=stale-token');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'invitee@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByText(
                'Dieser Einladungslink ist abgelaufen. Bitte um einen neuen Link bitten.',
            ),
        ).toBeInTheDocument();
    });

    it('shows a localized error for a spent or revoked invite', async () => {
        auth.register.mockRejectedValue(
            new ApiError({
                code: 'INVITE_INVALID',
                status: 400,
                field: null,
                message: 'no longer valid',
            }),
        );
        renderApp('/register?invite=spent-token');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'invitee@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByText(
                'Dieser Einladungslink ist ungültig. Bitte um einen neuen Link bitten.',
            ),
        ).toBeInTheDocument();
    });

    it('shows a distinct message when a pending account tries to log in', async () => {
        auth.login.mockRejectedValue(
            new ApiError({
                code: 'ACCOUNT_PENDING',
                status: 403,
                field: null,
                message: 'pending',
            }),
        );
        renderApp('/login');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }));

        expect(
            await screen.findByText('Dieses Konto wartet noch auf Freigabe.'),
        ).toBeInTheDocument();
    });

    it('shows a distinct message when a disabled account tries to log in', async () => {
        auth.login.mockRejectedValue(
            new ApiError({
                code: 'ACCOUNT_DISABLED',
                status: 403,
                field: null,
                message: 'disabled',
            }),
        );
        renderApp('/login');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Anmelden' }));

        expect(await screen.findByText('Dieses Konto wurde deaktiviert.')).toBeInTheDocument();
    });

    it('logs out from the admin area via the avatar menu and returns to the public area', async () => {
        auth.refresh.mockResolvedValue(admin);
        auth.logout.mockResolvedValue(undefined);
        renderApp('/admin');

        await userEvent.click(await screen.findByRole('button', { name: 'Konto' }));
        await userEvent.click(screen.getByRole('menuitem', { name: 'Abmelden' }));

        expect(
            await screen.findByRole('heading', { name: 'Öffentliche Turniere' }),
        ).toBeInTheDocument();
        // Signing out swaps the avatar for the sign-in entry and drops the admin tab.
        expect(screen.queryByRole('button', { name: 'Konto' })).not.toBeInTheDocument();
        expect(
            screen.getByRole('button', { name: 'Als Organisator anmelden' }),
        ).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Verwaltung' })).not.toBeInTheDocument();
        expect(auth.logout).toHaveBeenCalled();
    });
});

describe('header sign-in popover', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        auth.refresh.mockRejectedValue(noSession());
        tournamentRepo.list.mockResolvedValue([]);
    });

    it('signs a visitor in from the header and lands in the admin area', async () => {
        auth.login.mockResolvedValue(admin);
        renderApp('/public');

        await userEvent.click(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        );
        const popover = screen.getByRole('dialog', { name: 'Anmelden' });
        await userEvent.type(within(popover).getByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(within(popover).getByLabelText('Passwort'), 'correct-password');
        await userEvent.click(within(popover).getByRole('button', { name: 'Anmelden' }));

        const nav = screen.getByRole('navigation', { name: 'Hauptnavigation' });
        expect(await within(nav).findByRole('link', { name: 'Verwaltung' })).toHaveAttribute(
            'aria-current',
            'page',
        );
        // The session replaces the sign-in entry with the avatar.
        expect(screen.getByRole('button', { name: 'Konto' })).toBeInTheDocument();
        expect(
            screen.queryByRole('button', { name: 'Als Organisator anmelden' }),
        ).not.toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('still offers sign-in when the session check itself fails', async () => {
        auth.refresh.mockRejectedValue(
            new ApiError({ code: 'UNKNOWN', status: 500, field: null, message: 'boom' }),
        );
        renderApp('/public');

        // Without a confirmed session the admin tab stays hidden, so this is the only way in.
        expect(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        ).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Verwaltung' })).not.toBeInTheDocument();
    });

    it('starts closed again after the visitor followed a link out of it', async () => {
        const { router } = renderApp('/public');
        await userEvent.click(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        );

        await userEvent.click(screen.getByRole('link', { name: 'Jetzt erstellen' }));
        expect(await screen.findByRole('heading', { name: 'Konto erstellen' })).toBeInTheDocument();
        await router.navigate({ to: '/public' });

        const trigger = await screen.findByRole('button', { name: 'Als Organisator anmelden' });
        expect(trigger).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it.each(['/login', '/register'])(
        'leaves the sign-in entry out on %s, which has its own form',
        async (path) => {
            const { queryClient } = renderApp(path);

            expect(await screen.findByLabelText('E-Mail')).toBeInTheDocument();
            // Wait for the signed-out session to resolve, or the entry would be absent anyway.
            await waitFor(() =>
                expect(queryClient.getQueryState(sessionQueryKey())?.status).toBe('success'),
            );
            expect(
                screen.queryByRole('button', { name: 'Als Organisator anmelden' }),
            ).not.toBeInTheDocument();
        },
    );
});

describe('header avatar outside the admin area', () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    it('shows for a signed-in admin who opens a non-admin page first', async () => {
        auth.refresh.mockResolvedValue(admin);
        renderApp('/public');

        await userEvent.click(await screen.findByRole('button', { name: 'Konto' }));
        expect(screen.getByText('organizer@example.com')).toBeInTheDocument();
    });

    it('closes its menu on Escape and hands focus back to the avatar', async () => {
        auth.refresh.mockResolvedValue(admin);
        renderApp('/public');
        const avatar = await screen.findByRole('button', { name: 'Konto' });
        await userEvent.click(avatar);
        screen.getByRole('menuitem', { name: 'Abmelden' }).focus();

        await userEvent.keyboard('{Escape}');

        expect(screen.queryByRole('menu')).not.toBeInTheDocument();
        expect(avatar).toHaveFocus();
    });

    it('stays hidden when nobody is signed in', async () => {
        auth.refresh.mockRejectedValue(noSession());
        renderApp('/public');

        // The sign-in entry only shows once the session resolved as signed out.
        expect(
            await screen.findByRole('button', { name: 'Als Organisator anmelden' }),
        ).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Konto' })).not.toBeInTheDocument();
    });
});
