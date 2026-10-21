import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { Player } from '@/domain/player.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: { register: vi.fn(), login: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}));
vi.mock('@/tournament-api/tournament.repository.ts', () => ({
    TournamentRepository: {
        list: vi.fn(),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));
vi.mock('@/team-api/team.repository.ts', () => ({
    TeamRepository: {
        list: vi.fn(),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const teams = vi.mocked(TeamRepository);

const admin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

function tournament(status: Tournament['status'], maxTeams = 2): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams,
        settings: { allowGrandTichu: false },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(id: string, name: string, playerOne: string, playerTwo: string): Team {
    const players: readonly [Player, Player] = [
        { id: `${id}-p1`, teamId: id, name: playerOne },
        { id: `${id}-p2`, teamId: id, name: playerTwo },
    ];
    return { id, tournamentId: 't-1', name, players };
}

function duplicateNameError(field: string): ApiError {
    return new ApiError({
        code: 'DUPLICATE_TEAM_NAME',
        status: 409,
        field,
        message: 'Team name already taken',
    });
}

const fillTeamForm = async (values: { name: string; playerOne: string; playerTwo: string }) => {
    await userEvent.type(screen.getByLabelText('Teamname'), values.name);
    await userEvent.type(screen.getByLabelText('Spieler 1'), values.playerOne);
    await userEvent.type(screen.getByLabelText('Spieler 2'), values.playerTwo);
    await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));
};

beforeEach(() => {
    vi.clearAllMocks();
    // Drop any queued `mockResolvedValueOnce` leftovers from earlier tests.
    teams.list.mockReset();
    auth.refresh.mockResolvedValue(admin);
});

describe('setup-phase team management', () => {
    it('adds a team through the dialog and shows it in the list', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list
            .mockResolvedValueOnce([])
            .mockResolvedValueOnce([team('tm-1', 'Team Ass', 'Alice', 'Bob')]);
        teams.create.mockResolvedValue(team('tm-1', 'Team Ass', 'Alice', 'Bob'));

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Team hinzufügen' }));
        await fillTeamForm({ name: 'Team Ass', playerOne: 'Alice', playerTwo: 'Bob' });

        expect(teams.create).toHaveBeenCalledWith('t-1', {
            name: 'Team Ass',
            playerOneName: 'Alice',
            playerTwoName: 'Bob',
        });
        expect(await screen.findByText('Team Ass')).toBeInTheDocument();
        expect(screen.getByText('Alice & Bob')).toBeInTheDocument();
    });

    it('rejects blank fields locally without calling the backend', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([]);

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Team hinzufügen' }));
        await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

        expect(await screen.findByText('Der Teamname ist erforderlich.')).toBeInTheDocument();
        expect(screen.getAllByText('Der Spielername ist erforderlich.')).toHaveLength(2);
        expect(teams.create).not.toHaveBeenCalled();

        // Whitespace-only input is blank, as the backend's isBlank() check treats it.
        await userEvent.type(screen.getByLabelText('Teamname'), '   ');
        await userEvent.type(screen.getByLabelText('Spieler 1'), '  ');
        await userEvent.type(screen.getByLabelText('Spieler 2'), '	');
        await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

        expect(await screen.findByText('Der Teamname ist erforderlich.')).toBeInTheDocument();
        expect(screen.getAllByText('Der Spielername ist erforderlich.')).toHaveLength(2);
        expect(teams.create).not.toHaveBeenCalled();
    });

    it('rejects two identical players locally and anchors the message to player 2', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([]);

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Team hinzufügen' }));
        await fillTeamForm({ name: 'Team Ass', playerOne: 'Alice', playerTwo: 'alice' });

        expect(
            await screen.findByText('Die beiden Spieler müssen unterschiedlich sein.'),
        ).toBeInTheDocument();
        expect(teams.create).not.toHaveBeenCalled();
    });

    it('anchors a duplicate team name from the server to the name field', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([]);
        teams.create.mockRejectedValue(duplicateNameError('name'));

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Team hinzufügen' }));
        await fillTeamForm({ name: 'Team Ass', playerOne: 'Alice', playerTwo: 'Bob' });

        expect(
            await screen.findByText('Ein Team mit diesem Namen existiert bereits.'),
        ).toBeInTheDocument();
        expect(screen.getByLabelText('Teamname')).toHaveAttribute('aria-invalid', 'true');
    });

    it('renders a server-side capacity conflict as a form-level error', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([]);
        teams.create.mockRejectedValue(
            new ApiError({
                code: 'TEAM_LIMIT_REACHED',
                status: 409,
                field: null,
                message: 'limit reached',
            }),
        );

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Team hinzufügen' }));
        await fillTeamForm({ name: 'Team Ass', playerOne: 'Alice', playerTwo: 'Bob' });

        expect(
            await screen.findByText(
                'Dieses Turnier hat bereits die maximale Teamanzahl erreicht.',
            ),
        ).toBeInTheDocument();
    });

    it('disables the add button with a hint once the capacity is reached', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', 2));
        teams.list.mockResolvedValue([
            team('tm-1', 'Team Ass', 'Alice', 'Bob'),
            team('tm-2', 'Team König', 'Carol', 'Dave'),
        ]);

        renderApp('/admin/tournaments/t-1');

        expect(
            await screen.findByText('Die maximale Teamanzahl ist erreicht.'),
        ).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Team hinzufügen' })).toBeDisabled();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('edits a team through the dialog', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass', 'Alice', 'Bob')]);
        teams.update.mockResolvedValue(team('tm-1', 'Team Adler', 'Alice', 'Bob'));

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Bearbeiten' }));
        const nameInput = await screen.findByLabelText('Teamname');
        expect(nameInput).toHaveValue('Team Ass');
        await userEvent.clear(nameInput);
        await userEvent.type(nameInput, 'Team Adler');
        await userEvent.click(screen.getByRole('button', { name: 'Speichern' }));

        expect(teams.update).toHaveBeenCalledWith('t-1', 'tm-1', {
            name: 'Team Adler',
            playerOneName: 'Alice',
            playerTwoName: 'Bob',
        });
    });

    it('deletes a team after confirmation', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list
            .mockResolvedValueOnce([team('tm-1', 'Team Ass', 'Alice', 'Bob')])
            .mockResolvedValueOnce([]);
        teams.remove.mockResolvedValue(undefined);

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Löschen' }));
        const confirmDialog = await screen.findByRole('dialog', { name: 'Team löschen' });
        await userEvent.click(within(confirmDialog).getByRole('button', { name: 'Löschen' }));

        expect(teams.remove).toHaveBeenCalledWith('t-1', 'tm-1');
    });

    it('shows the team list read-only outside the setup phase', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass', 'Alice', 'Bob')]);

        renderApp('/admin/tournaments/t-1');

        expect(await screen.findByText('Team Ass')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Team hinzufügen' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Bearbeiten' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Löschen' })).not.toBeInTheDocument();
    });
});
