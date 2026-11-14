import { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';
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
vi.mock('@/tournament-api/tournament.repository.ts', async () => {
    const { mockTournamentRepositoryModule } =
        await import('@/test-helpers/tournament-repository.mock.ts');
    return mockTournamentRepositoryModule();
});
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

const SWITCH_NAME = 'Tische zufällig verteilen';

function tournament(status: Tournament['status'], shuffledTables = true): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: {
            grandTichuBonus: 200,
            doubleWinBonus: 200,
            totalRounds: 2,
            shuffledTables,
        },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        organizerRole: OrganizerRole.LEAD,
        leadOrganizerEmail: 'lead@example.com',
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

describe('shuffled tables switch on the detail info card', () => {
    it('shows the switch checked when the tournament shuffles its tables', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', true));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: SWITCH_NAME });
        expect(toggle).toHaveAttribute('aria-checked', 'true');
    });

    it('optimistically turns off and calls the repository when toggled', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', true));
        let resolveUpdate: (value: Tournament) => void = () => undefined;
        tournaments.setShuffledTables.mockReturnValue(
            new Promise<Tournament>((resolve) => {
                resolveUpdate = resolve;
            }),
        );
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: SWITCH_NAME });
        await userEvent.click(toggle);

        expect(tournaments.setShuffledTables).toHaveBeenCalledWith('t-1', false);
        await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'));

        resolveUpdate(tournament('SETUP', false));
    });

    it('rolls back when the repository rejects the change', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', true));
        tournaments.setShuffledTables.mockRejectedValue(new Error('offline'));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: SWITCH_NAME });
        await userEvent.click(toggle);

        await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'true'));
    });

    it('is enabled while the tournament is active', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', true));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: SWITCH_NAME });
        expect(toggle).toBeEnabled();
    });

    it('is disabled once the tournament is completed', async () => {
        tournaments.get.mockResolvedValue(tournament('COMPLETED', true));
        renderApp('/admin/tournaments/t-1');

        const toggle = await screen.findByRole('switch', { name: SWITCH_NAME });
        expect(toggle).toBeDisabled();
    });
});
