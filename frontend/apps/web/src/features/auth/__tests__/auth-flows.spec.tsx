import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
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

vi.mock('@/tournament-api/tournament.repository.ts', () => ({
    TournamentRepository: {
        list: vi.fn().mockResolvedValue([]),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournamentRepo = vi.mocked(TournamentRepository);
const admin = { id: 'admin-1', email: 'organizer@example.com' };

const noSession = () =>
    new ApiError({ code: 'UNAUTHENTICATED', status: 401, field: null, message: 'no session' });

describe('admin auth flows', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        auth.refresh.mockRejectedValue(noSession());
        // The admin dashboard fetches tournaments as soon as it mounts.
        tournamentRepo.list.mockResolvedValue([]);
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

        expect(await screen.findByRole('button', { name: 'Abmelden' })).toBeInTheDocument();
        expect(screen.getByText('organizer@example.com')).toBeInTheDocument();
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

        expect(await screen.findByRole('button', { name: 'Abmelden' })).toBeInTheDocument();
        expect(auth.register).toHaveBeenCalledWith(
            expect.objectContaining({ inviteToken: 'invite-token' }),
        );
        expect(auth.login).toHaveBeenCalled();
    });

    it('anchors an invite email mismatch to the email field', async () => {
        auth.register.mockRejectedValue(
            new ApiError({
                code: 'INVITE_EMAIL_MISMATCH',
                status: 400,
                field: null,
                message: 'wrong address',
            }),
        );
        renderApp('/register?invite=invite-token');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'someone-else@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(
            await screen.findByText(
                'Diese Einladung wurde für eine andere E-Mail-Adresse ausgestellt.',
            ),
        ).toBeInTheDocument();
        expect(screen.getByLabelText('E-Mail')).toHaveAttribute('aria-invalid', 'true');
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

    it('logs out from the admin area and returns to login', async () => {
        auth.refresh.mockResolvedValue(admin);
        auth.logout.mockResolvedValue(undefined);
        renderApp('/admin');

        await userEvent.click(await screen.findByRole('button', { name: 'Abmelden' }));

        expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
        expect(auth.logout).toHaveBeenCalled();
    });
});
