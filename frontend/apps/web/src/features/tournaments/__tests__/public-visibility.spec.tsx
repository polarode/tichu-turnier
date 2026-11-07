import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
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
        start: vi.fn(),
        rounds: vi.fn(),
        standings: vi.fn(),
        setTotalRounds: vi.fn(),
        startNextRound: vi.fn(),
        finish: vi.fn(),
        publish: vi.fn(),
        setPublicVisibility: vi.fn(),
        listPublicTournaments: vi.fn(),
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

function tournament(status: Tournament['status'], publicVisibility = false): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 2 },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility,
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue({ id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' });
    teams.list.mockResolvedValue([]);
    tournaments.standings.mockResolvedValue([]);
    tournaments.rounds.mockResolvedValue([]);
});

describe('public visibility switch on the detail info card', () => {
    it('shows the switch in the off state for a private tournament', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', false));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: 'Öffentliche Sichtbarkeit' });
        expect(toggle).toHaveAttribute('aria-checked', 'false');
    });

    it('optimistically turns on and calls the repository when toggled', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', false));
        let resolveUpdate: (value: Tournament) => void = () => undefined;
        tournaments.setPublicVisibility.mockReturnValue(
            new Promise<Tournament>((resolve) => {
                resolveUpdate = resolve;
            }),
        );
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: 'Öffentliche Sichtbarkeit' });
        await userEvent.click(toggle);

        expect(tournaments.setPublicVisibility).toHaveBeenCalledWith('t-1', true);
        await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'));

        resolveUpdate(tournament('SETUP', true));
    });

    it('rolls back when the repository rejects the change', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', false));
        tournaments.setPublicVisibility.mockRejectedValue(new Error('offline'));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: 'Öffentliche Sichtbarkeit' });
        await userEvent.click(toggle);

        await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'));
    });

    it('is present and usable once the tournament is published', async () => {
        tournaments.get.mockResolvedValue(tournament('PUBLISHED', false));
        tournaments.setPublicVisibility.mockResolvedValue(tournament('PUBLISHED', true));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: 'Öffentliche Sichtbarkeit' });
        expect(toggle).toBeEnabled();
        await userEvent.click(toggle);

        expect(tournaments.setPublicVisibility).toHaveBeenCalledWith('t-1', true);
    });
});
