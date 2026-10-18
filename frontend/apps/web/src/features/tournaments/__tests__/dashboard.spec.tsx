import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { Player } from '@/domain/player.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
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

const admin = { id: 'admin-1', email: 'organizer@example.com' };

function tournament(id: string, name: string, status: Tournament['status']): Tournament {
    return {
        id,
        name,
        description: '',
        maxTeams: 16,
        settings: { allowGrandTichu: true },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(id: string, tournamentId: string): Team {
    const players: readonly [Player, Player] = [
        { id: `${id}-p1`, teamId: id, name: 'Alice' },
        { id: `${id}-p2`, teamId: id, name: 'Bob' },
    ];
    return { id, tournamentId, name: `Team ${id}`, players };
}

describe('dashboard', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        auth.refresh.mockResolvedValue(admin);
    });

    it('lists the admin tournaments with status and team count', async () => {
        tournaments.list.mockResolvedValue([
            tournament('t-1', 'Spring Classic', 'SETUP'),
            tournament('t-2', 'Autumn Cup', 'ACTIVE'),
        ]);
        teams.list.mockImplementation((tournamentId: string) =>
            Promise.resolve([team(`${tournamentId}-a`, tournamentId)]),
        );

        renderApp('/admin');

        const springRow = (await screen.findByText('Spring Classic')).closest('li');
        expect(springRow).not.toBeNull();
        expect(within(springRow!).getByText('Vorbereitung')).toBeInTheDocument();
        expect(within(springRow!).getByText('Teams: 1')).toBeInTheDocument();
        expect(within(springRow!).getByRole('link', { name: 'Öffnen' })).toHaveAttribute(
            'href',
            '/admin/tournaments/t-1',
        );

        const autumnRow = screen.getByText('Autumn Cup').closest('li');
        expect(autumnRow).not.toBeNull();
        expect(within(autumnRow!).getByText('Läuft')).toBeInTheDocument();

        expect(screen.getByRole('link', { name: 'Neues Turnier' })).toHaveAttribute(
            'href',
            '/admin/tournaments/new',
        );
    });

    it('shows an empty state when the admin has no tournaments', async () => {
        tournaments.list.mockResolvedValue([]);

        renderApp('/admin');

        expect(
            await screen.findByRole('heading', { name: 'Noch keine Turniere' }),
        ).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Neues Turnier' })).toBeInTheDocument();
    });
});
