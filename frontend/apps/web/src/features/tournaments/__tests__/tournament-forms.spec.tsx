import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/tournament-api/tournament.repository.ts', () => ({
    TournamentRepository: {
        list: vi.fn().mockResolvedValue([]),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: {
        register: vi.fn(),
        login: vi.fn(),
        refresh: vi.fn().mockResolvedValue({
            id: 'admin-1',
            email: 'organizer@example.com',
            role: 'ADMIN' as const,
        }),
        logout: vi.fn(),
    },
}));

vi.mock('@/team-api/team.repository.ts', () => ({
    TeamRepository: {
        list: vi.fn().mockResolvedValue([]),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));

const tournamentRepo = vi.mocked(TournamentRepository);
const teamRepo = vi.mocked(TeamRepository);
const authRepo = vi.mocked(AuthRepository);

const adminSession = {
    id: 'admin-1',
    email: 'organizer@example.com',
    role: 'ADMIN' as const,
};

const setupTournament = {
    id: 't-1',
    name: 'Jahresturnier',
    description: '',
    maxTeams: 8,
    settings: { allowGrandTichu: false },
    status: 'SETUP' as const,
    createdAt: new Date('2025-01-01T10:00:00Z'),
    updatedAt: new Date('2025-01-01T10:00:00Z'),
};

const fillAndSubmitCreateForm = async (overrides: {
    name?: string;
    description?: string;
    maxTeams?: string;
    allowGrandTichu?: boolean;
} = {}) => {
    await userEvent.type(await screen.findByLabelText('Name'), overrides.name ?? 'Jahresturnier');
    if (overrides.description !== undefined) {
        await userEvent.type(screen.getByLabelText('Beschreibung'), overrides.description);
    }
    await userEvent.clear(screen.getByLabelText('Maximale Teamanzahl'));
    await userEvent.type(
        screen.getByLabelText('Maximale Teamanzahl'),
        overrides.maxTeams ?? '8',
    );
    if (overrides.allowGrandTichu === true) {
        await userEvent.click(screen.getByLabelText('Grand Tichu erlauben'));
    }
    await userEvent.click(screen.getByRole('button', { name: 'Turnier erstellen' }));
};

describe('tournament create/edit forms', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        // resetAllMocks also strips the module-level refresh implementation, so the
        // admin route guard would fail on an undefined session. Re-seed it every test.
        authRepo.refresh.mockResolvedValue(adminSession);
        tournamentRepo.list.mockResolvedValue([]);
        // The tournament detail screen renders the teams section; seed its list too.
        teamRepo.list.mockResolvedValue([]);
    });

    it('validates the create form before submitting', async () => {
        renderApp('/admin/tournaments/new');
        await screen.findByLabelText('Name');

        await userEvent.click(screen.getByRole('button', { name: 'Turnier erstellen' }));

        expect(await screen.findByText('Der Name ist erforderlich.')).toBeInTheDocument();
        expect(tournamentRepo.create).not.toHaveBeenCalled();
    });

    it('creates a tournament and lands on its detail page', async () => {
        tournamentRepo.create.mockResolvedValue(setupTournament);
        tournamentRepo.get.mockResolvedValue(setupTournament);
        renderApp('/admin/tournaments/new');

        await fillAndSubmitCreateForm({ allowGrandTichu: true });

        expect(tournamentRepo.create).toHaveBeenCalledWith({
            name: 'Jahresturnier',
            description: '',
            maxTeams: 8,
            settings: { allowGrandTichu: true },
        });
        expect(await screen.findByText('Jahresturnier')).toBeInTheDocument();
        expect(tournamentRepo.get).toHaveBeenCalledWith('t-1');
    });

    it('anchors MAX_TEAMS_BELOW_TEAM_COUNT to the max-teams field', async () => {
        tournamentRepo.create.mockRejectedValue(
            new ApiError({
                code: 'MAX_TEAMS_BELOW_TEAM_COUNT',
                status: 422,
                field: null,
                message: 'too low',
            }),
        );
        renderApp('/admin/tournaments/new');

        await fillAndSubmitCreateForm();

        expect(
            await screen.findByText('Das Maximum darf nicht unter der aktuellen Teamanzahl liegen.'),
        ).toBeInTheDocument();
    });

    it('shows INVALID_INPUT as a form-level message', async () => {
        tournamentRepo.create.mockRejectedValue(
            new ApiError({ code: 'INVALID_INPUT', status: 422, field: null, message: 'bad' }),
        );
        renderApp('/admin/tournaments/new');

        await fillAndSubmitCreateForm();

        expect(await screen.findByText('Bitte die Eingaben prüfen.')).toBeInTheDocument();
    });

    it('rejects a max team count below 2 on the client', async () => {
        renderApp('/admin/tournaments/new');

        await fillAndSubmitCreateForm({ maxTeams: '1' });

        expect(
            await screen.findByText('Das Turnier benötigt mindestens 2 Teams.'),
        ).toBeInTheDocument();
        expect(tournamentRepo.create).not.toHaveBeenCalled();
    });

    it('rejects a name longer than 100 characters on the client', async () => {
        renderApp('/admin/tournaments/new');

        await fillAndSubmitCreateForm({ name: 'x'.repeat(101) });

        expect(
            await screen.findByText('Der Name darf höchstens 100 Zeichen lang sein.'),
        ).toBeInTheDocument();
        expect(tournamentRepo.create).not.toHaveBeenCalled();
    });

    it('prefills the edit form and updates the tournament', async () => {
        // First get feeds the edit form's prefill; the detail page refetches after the
        // mutation (the list invalidation also covers the detail key), so the second
        // get must return the updated name.
        tournamentRepo.get
            .mockResolvedValueOnce(setupTournament)
            .mockResolvedValue({ ...setupTournament, name: 'Renommiert' });
        tournamentRepo.update.mockResolvedValue({ ...setupTournament, name: 'Renommiert' });
        renderApp('/admin/tournaments/t-1/edit');

        const nameInput = await screen.findByLabelText('Name');
        expect(nameInput).toHaveValue('Jahresturnier');
        expect(screen.getByLabelText('Grand Tichu erlauben')).not.toBeChecked();

        await userEvent.clear(nameInput);
        await userEvent.type(nameInput, 'Renommiert');
        await userEvent.click(screen.getByRole('button', { name: 'Änderungen speichern' }));

        expect(tournamentRepo.update).toHaveBeenCalledWith('t-1', {
            name: 'Renommiert',
            description: '',
            maxTeams: 8,
            settings: { allowGrandTichu: false },
        });
        expect(await screen.findByText('Renommiert')).toBeInTheDocument();
        expect(tournamentRepo.get).toHaveBeenCalledWith('t-1');
    });

    it('blocks editing a tournament that is no longer in setup', async () => {
        tournamentRepo.get.mockResolvedValue({ ...setupTournament, status: 'ACTIVE' as const });
        renderApp('/admin/tournaments/t-1/edit');

        expect(
            await screen.findByText('Dieses Turnier kann nicht mehr bearbeitet werden.'),
        ).toBeInTheDocument();
        expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();
        expect(tournamentRepo.update).not.toHaveBeenCalled();
    });

    it('anchors a MAX_TEAMS_BELOW_TEAM_COUNT update rejection to the max-teams field on the edit route', async () => {
        tournamentRepo.get.mockResolvedValue(setupTournament);
        tournamentRepo.update.mockRejectedValue(
            new ApiError({
                code: 'MAX_TEAMS_BELOW_TEAM_COUNT',
                status: 422,
                field: null,
                message: 'too low',
            }),
        );
        renderApp('/admin/tournaments/t-1/edit');

        await userEvent.click(await screen.findByRole('button', { name: 'Änderungen speichern' }));

        expect(
            await screen.findByText('Das Maximum darf nicht unter der aktuellen Teamanzahl liegen.'),
        ).toBeInTheDocument();
        expect(screen.getByLabelText('Maximale Teamanzahl')).toBeInvalid();
        expect(screen.queryByText('Dieses Turnier kann nicht mehr bearbeitet werden.')).not.toBeInTheDocument();
    });

    it('replaces the edit form with the conflict message when the server rejects with TOURNAMENT_NOT_EDITABLE', async () => {
        tournamentRepo.get.mockResolvedValue(setupTournament);
        tournamentRepo.update.mockRejectedValue(
            new ApiError({
                code: 'TOURNAMENT_NOT_EDITABLE',
                status: 409,
                field: null,
                message: 'conflict',
            }),
        );
        renderApp('/admin/tournaments/t-1/edit');

        await userEvent.click(await screen.findByRole('button', { name: 'Änderungen speichern' }));

        expect(
            await screen.findByText('Dieses Turnier kann nicht mehr bearbeitet werden.'),
        ).toBeInTheDocument();
        expect(screen.queryByLabelText('Name')).not.toBeInTheDocument();
    });

    it('hides the edit entry on the detail page for a non-setup tournament', async () => {
        tournamentRepo.get.mockResolvedValue({ ...setupTournament, status: 'ACTIVE' as const });
        renderApp('/admin/tournaments/t-1');

        expect(await screen.findByText('Jahresturnier')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Bearbeiten' })).not.toBeInTheDocument();
    });

    it('links to the edit form on the detail page for a setup tournament', async () => {
        tournamentRepo.get.mockResolvedValue(setupTournament);
        renderApp('/admin/tournaments/t-1');

        expect(
            await screen.findByRole('link', { name: 'Bearbeiten' }),
        ).toHaveAttribute('href', '/admin/tournaments/t-1/edit');
    });
});
