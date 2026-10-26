import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { Player } from '@/domain/player.domain.ts';
import type { Round } from '@/domain/round.domain.ts';
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
        start: vi.fn(),
        rounds: vi.fn(),
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

const admin = { id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' as const };

function tournament(status: Tournament['status']): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { allowGrandTichu: false },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(id: string, name: string): Team {
    const players: readonly [Player, Player] = [
        { id: `${id}-p1`, teamId: id, name: `${name} 1` },
        { id: `${id}-p2`, teamId: id, name: `${name} 2` },
    ];
    return { id, tournamentId: 't-1', name, players };
}

function round(number: number, tables: Round['tables']): Round {
    return { number, tables };
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue(admin);
});

describe('admin start action', () => {
    it('starts the tournament after confirmation and shows the ACTIVE badge', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        tournaments.start.mockResolvedValue({
            tournament: tournament('ACTIVE'),
            round: round(1, [{ tableNumber: 1, homeTeamId: 'team-a', awayTeamId: 'team-b' }]),
        });
        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Turnier starten' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Bestätigen' }));

        expect(tournaments.start).toHaveBeenCalledWith('t-1');
        await waitFor(() =>
            expect(screen.getByText('Läuft')).toBeInTheDocument(),
        );
    });

    it('blocks the start button with a hint while there are fewer than two teams', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha')]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        const button = await screen.findByRole('button', { name: 'Turnier starten' });
        expect(button).toBeDisabled();
        expect(screen.getByText(/mindestens zwei Teams/)).toBeInTheDocument();
        expect(tournaments.start).not.toHaveBeenCalled();
    });

    it('blocks the start button with a hint while the team count is odd', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([
            team('team-a', 'Alpha'),
            team('team-b', 'Beta'),
            team('team-c', 'Gamma'),
        ]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        const button = await screen.findByRole('button', { name: 'Turnier starten' });
        expect(button).toBeDisabled();
        expect(screen.getByText(/ungerade Teamanzahl/)).toBeInTheDocument();
    });

    it('shows the localized server error when starting is rejected', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        tournaments.start.mockRejectedValue(
            new ApiError({
                code: 'TOURNAMENT_NOT_STARTABLE',
                status: 409,
                field: null,
                message: 'not startable',
            }),
        );
        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Turnier starten' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Bestätigen' }));

        expect(
            await screen.findByText(
                'Dieses Turnier kann nicht gestartet werden, weil es nicht mehr in der Vorbereitung ist.',
            ),
        ).toBeInTheDocument();
    });
});

describe('rounds section', () => {
    it('renders tables with team names per round', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([
            round(1, [
                { tableNumber: 1, homeTeamId: 'team-a', awayTeamId: 'team-b' },
                { tableNumber: 2, homeTeamId: 'team-b', awayTeamId: 'team-a' },
            ]),
        ]);
        renderApp('/admin/tournaments/t-1');

        const section = await screen.findByRole('region', { name: 'Runden' });
        expect(within(section).getByText('Runde 1')).toBeInTheDocument();
        expect(within(section).getByText('Tisch 1')).toBeInTheDocument();
        const tableRow = within(section).getByText((_, element) =>
            element?.textContent === 'Tisch 1: Alpha vs Beta',
        );
        expect(tableRow).toBeInTheDocument();
    });

    it('shows no rounds section while the tournament is in setup', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        await screen.findByRole('button', { name: 'Turnier starten' });
        expect(screen.queryByRole('region', { name: 'Runden' })).not.toBeInTheDocument();
    });
});
