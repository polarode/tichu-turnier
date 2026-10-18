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

    it('registers, signs in and lands in the admin area', async () => {
        auth.register.mockResolvedValue(admin);
        auth.login.mockResolvedValue(admin);
        renderApp('/register');

        await userEvent.type(await screen.findByLabelText('E-Mail'), 'organizer@example.com');
        await userEvent.type(await screen.findByLabelText('Passwort'), 'long-enough-password');
        await userEvent.click(screen.getByRole('button', { name: 'Konto erstellen' }));

        expect(await screen.findByRole('button', { name: 'Abmelden' })).toBeInTheDocument();
        expect(auth.login).toHaveBeenCalled();
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
