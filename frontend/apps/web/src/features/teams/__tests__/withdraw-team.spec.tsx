import { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { Player } from '@/domain/player.domain.ts';
import type { Team, TeamWithdrawal } from '@/domain/team.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { publicStatisticsQueryKey } from '@/shared/constants/query-keys.ts';
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
        withdraw: vi.fn(),
        undoWithdrawal: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const teams = vi.mocked(TeamRepository);

const admin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

function tournament(status: Tournament['status']): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 4,
        settings: { grandTichuBonus: 100, doubleWinBonus: 200, totalRounds: 4 },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        organizerRole: OrganizerRole.LEAD,
        leadOrganizerEmail: 'lead@example.com',
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(id: string, name: string, withdrawal: TeamWithdrawal | null = null): Team {
    const players: readonly [Player, Player] = [
        { id: `${id}-p1`, teamId: id, name: 'Alice' },
        { id: `${id}-p2`, teamId: id, name: 'Bob' },
    ];
    return { withdrawal, id, tournamentId: 't-1', name, players };
}

const disqualified: TeamWithdrawal = {
    reason: 'DISQUALIFIED',
    compensation: 'HALF',
    roundNumber: 2,
    withdrawnAt: new Date('2026-11-14T10:00:00Z'),
};

beforeEach(() => {
    vi.clearAllMocks();
    teams.list.mockReset();
    tournaments.rounds.mockResolvedValue([]);
    auth.refresh.mockResolvedValue(admin);
});

describe('withdrawing a team once the tournament has started', () => {
    it('withdraws a team with the chosen reason and compensation', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list
            .mockResolvedValueOnce([team('tm-1', 'Team Ass')])
            .mockResolvedValueOnce([team('tm-1', 'Team Ass', disqualified)]);
        teams.withdraw.mockResolvedValue(disqualified);

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(await screen.findByRole('button', { name: 'Team zurückziehen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Team zurückziehen' });
        await userEvent.click(within(dialog).getByRole('radio', { name: 'Disqualifiziert' }));
        await userEvent.click(within(dialog).getByRole('radio', { name: 'Halb (1 SP + 50 TP)' }));
        await userEvent.click(within(dialog).getByRole('button', { name: 'Zurückziehen' }));

        expect(teams.withdraw).toHaveBeenCalledWith('t-1', 'tm-1', {
            reason: 'DISQUALIFIED',
            compensation: 'HALF',
        });
        expect(await screen.findByText('Disqualifiziert')).toBeInTheDocument();
    });

    it('marks the reason and the compensation as required choices', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass')]);

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(await screen.findByRole('button', { name: 'Team zurückziehen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Team zurückziehen' });
        expect(within(dialog).getByRole('radiogroup', { name: 'Grund' })).toHaveAttribute(
            'aria-required',
            'true',
        );
        expect(
            within(dialog).getByRole('radiogroup', { name: 'Ausgleich für offene Spiele' }),
        ).toHaveAttribute('aria-required', 'true');
    });

    it('refreshes the public statistics once a team is withdrawn', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass')]);
        teams.withdraw.mockResolvedValue(disqualified);

        const { queryClient } = renderApp('/admin/tournaments/t-1?tab=teams');
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

        await userEvent.click(await screen.findByRole('button', { name: 'Team zurückziehen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Team zurückziehen' });
        await userEvent.click(within(dialog).getByRole('radio', { name: 'Disqualifiziert' }));
        await userEvent.click(within(dialog).getByRole('radio', { name: 'Kein Ausgleich' }));
        await userEvent.click(within(dialog).getByRole('button', { name: 'Zurückziehen' }));

        await waitFor(() =>
            expect(invalidate).toHaveBeenCalledWith({
                queryKey: publicStatisticsQueryKey('t-1'),
            }),
        );
    });

    it('requires an explicit reason and compensation before withdrawing', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass')]);

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(await screen.findByRole('button', { name: 'Team zurückziehen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Team zurückziehen' });
        await userEvent.click(within(dialog).getByRole('button', { name: 'Zurückziehen' }));

        expect(await screen.findByText('Bitte einen Grund wählen.')).toBeInTheDocument();
        expect(screen.getByText('Bitte einen Ausgleich wählen.')).toBeInTheDocument();
        expect(teams.withdraw).not.toHaveBeenCalled();
    });

    it('shows a localized error in the dialog when the withdrawal is refused', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass')]);
        teams.withdraw.mockRejectedValue(
            new ApiError({
                code: 'TEAM_ALREADY_WITHDRAWN',
                status: 409,
                field: null,
                message: 'already withdrawn',
            }),
        );

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(await screen.findByRole('button', { name: 'Team zurückziehen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Team zurückziehen' });
        await userEvent.click(within(dialog).getByRole('radio', { name: 'Disqualifiziert' }));
        await userEvent.click(within(dialog).getByRole('radio', { name: 'Kein Ausgleich' }));
        await userEvent.click(within(dialog).getByRole('button', { name: 'Zurückziehen' }));

        expect(
            await within(dialog).findByText('Das Team hat sich bereits zurückgezogen.'),
        ).toBeInTheDocument();
    });

    it('shows the reason badge on a withdrawn team, which offers no withdraw action', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass', disqualified)]);

        renderApp('/admin/tournaments/t-1?tab=teams');

        expect(await screen.findByText('Disqualifiziert')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Team zurückziehen' })).not.toBeInTheDocument();
    });

    it('undoes a withdrawal after confirmation', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list
            .mockResolvedValueOnce([team('tm-1', 'Team Ass', disqualified)])
            .mockResolvedValueOnce([team('tm-1', 'Team Ass')]);
        teams.undoWithdrawal.mockResolvedValue(undefined);

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(
            await screen.findByRole('button', { name: 'Rückzug rückgängig machen' }),
        );
        const confirm = await screen.findByRole('dialog', {
            name: 'Rückzug rückgängig machen?',
        });
        await userEvent.click(within(confirm).getByRole('button', { name: 'Rückgängig machen' }));

        expect(teams.undoWithdrawal).toHaveBeenCalledWith('t-1', 'tm-1');
    });

    it('explains that an undo reopens the forfeited games', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass', disqualified)]);

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(
            await screen.findByRole('button', { name: 'Rückzug rückgängig machen' }),
        );
        const confirm = await screen.findByRole('dialog', {
            name: 'Rückzug rückgängig machen?',
        });
        expect(
            within(confirm).getByText(
                'Das Team nimmt wieder am Turnier teil. Kampflos gewertete Spiele werden wieder offen.',
            ),
        ).toBeInTheDocument();
    });

    it('shows the WITHDRAWAL_FINAL message when the undo is refused', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass', disqualified)]);
        teams.undoWithdrawal.mockRejectedValue(
            new ApiError({
                code: 'WITHDRAWAL_FINAL',
                status: 409,
                field: null,
                message: 'final',
            }),
        );

        renderApp('/admin/tournaments/t-1?tab=teams');

        await userEvent.click(
            await screen.findByRole('button', { name: 'Rückzug rückgängig machen' }),
        );
        const confirm = await screen.findByRole('dialog', {
            name: 'Rückzug rückgängig machen?',
        });
        await userEvent.click(within(confirm).getByRole('button', { name: 'Rückgängig machen' }));

        expect(
            await screen.findByText('Der Rückzug kann nicht mehr rückgängig gemacht werden.'),
        ).toBeInTheDocument();
    });

    it('hides the undo action once the tournament is published', async () => {
        tournaments.get.mockResolvedValue(tournament('PUBLISHED'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass', disqualified)]);

        renderApp('/admin/tournaments/t-1?tab=teams');

        expect(await screen.findByText('Disqualifiziert')).toBeInTheDocument();
        expect(
            screen.queryByRole('button', { name: 'Rückzug rückgängig machen' }),
        ).not.toBeInTheDocument();
    });
});

describe('team withdrawal in the setup phase', () => {
    it('offers no withdraw action before the tournament starts', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('tm-1', 'Team Ass')]);

        renderApp('/admin/tournaments/t-1');

        expect(await screen.findByText('Team Ass')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Team zurückziehen' })).not.toBeInTheDocument();
    });
});
