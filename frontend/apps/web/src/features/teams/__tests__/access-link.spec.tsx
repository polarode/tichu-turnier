import { screen, waitFor, within } from '@testing-library/react';
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
        revealAccessLink: vi.fn(),
        rotateAccessLink: vi.fn(),
        revokeAccessLink: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const teams = vi.mocked(TeamRepository);

const admin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

function tournament(): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 8,
        settings: { allowGrandTichu: false },
        status: 'SETUP',
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(): Team {
    const players: readonly [Player, Player] = [
        { id: 'tm-1-p1', teamId: 'tm-1', name: 'Alice' },
        { id: 'tm-1-p2', teamId: 'tm-1', name: 'Bob' },
    ];
    return { id: 'tm-1', tournamentId: 't-1', name: 'Team Ass', players };
}

function revealedError(): ApiError {
    return new ApiError({
        code: 'LINK_ALREADY_REVEALED',
        status: 409,
        field: null,
        message: 'already revealed',
    });
}

const LINK_V1 = 'https://tichu.example/team/login?token=first-token';
const LINK_V2 = 'https://tichu.example/team/login?token=second-token';

beforeEach(() => {
    vi.clearAllMocks();
    teams.list.mockReset();
    auth.refresh.mockResolvedValue(admin);
    Object.defineProperty(navigator, 'clipboard', {
        value: { writeText: vi.fn().mockResolvedValue(undefined) },
        configurable: true,
    });
});

describe('team access link (reveal, rotate, revoke)', () => {
    it('reveals the link on open and shows the copyable link and its QR', async () => {
        tournaments.get.mockResolvedValue(tournament());
        teams.list.mockResolvedValue([team()]);
        teams.revealAccessLink.mockResolvedValue({ token: 'first-token', link: LINK_V1 });

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Zugangs-Link' }));

        expect(teams.revealAccessLink).toHaveBeenCalledWith('t-1', 'tm-1');
        expect(await screen.findByText(LINK_V1)).toBeInTheDocument();
        expect(screen.getByRole('img', { name: 'QR-Code für den Zugangs-Link' })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Kopieren' }));
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith(LINK_V1);
        expect(await screen.findByText('Kopiert!')).toBeInTheDocument();
    });

    it('rotates with confirmation and shows the replacement token once', async () => {
        tournaments.get.mockResolvedValue(tournament());
        teams.list.mockResolvedValue([team()]);
        teams.revealAccessLink
            .mockResolvedValueOnce({ token: 'first-token', link: LINK_V1 })
            .mockResolvedValueOnce({ token: 'second-token', link: LINK_V2 });
        teams.rotateAccessLink.mockResolvedValue(undefined);

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Zugangs-Link' }));
        await screen.findByText(LINK_V1);

        await userEvent.click(screen.getByRole('button', { name: 'Rotieren' }));
        const rotateConfirm = await screen.findByRole('dialog', { name: 'Zugangs-Link rotieren?' });
        await userEvent.click(within(rotateConfirm).getByRole('button', { name: 'Rotieren' }));

        expect(teams.rotateAccessLink).toHaveBeenCalledWith('t-1', 'tm-1');
        expect(teams.revealAccessLink).toHaveBeenCalledTimes(2);
        expect(await screen.findByText(LINK_V2)).toBeInTheDocument();
        expect(screen.queryByText(LINK_V1)).not.toBeInTheDocument();
    });

    it('revokes with confirmation and closes the dialog', async () => {
        tournaments.get.mockResolvedValue(tournament());
        teams.list.mockResolvedValue([team()]);
        teams.revealAccessLink.mockResolvedValue({ token: 'first-token', link: LINK_V1 });
        teams.revokeAccessLink.mockResolvedValue(undefined);

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Zugangs-Link' }));
        await screen.findByText(LINK_V1);

        await userEvent.click(screen.getByRole('button', { name: 'Widerrufen' }));
        const revokeConfirm = await screen.findByRole('dialog', { name: 'Zugangs-Link widerrufen?' });
        await userEvent.click(within(revokeConfirm).getByRole('button', { name: 'Widerrufen' }));

        expect(teams.revokeAccessLink).toHaveBeenCalledWith('t-1', 'tm-1');
        await waitFor(() => expect(screen.queryByText(LINK_V1)).not.toBeInTheDocument());
        expect(screen.getByRole('button', { name: 'Zugangs-Link' })).toBeInTheDocument();
    });

    it('offers rotation as recovery when the link was already revealed', async () => {
        tournaments.get.mockResolvedValue(tournament());
        teams.list.mockResolvedValue([team()]);
        teams.revealAccessLink.mockRejectedValue(revealedError());

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Zugangs-Link' }));

        expect(
            await screen.findByText(
                'Dieser Zugangs-Link wurde bereits angezeigt oder ist deaktiviert. Rotiere ihn, um einen neuen zu erhalten.',
            ),
        ).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Rotieren' })).toBeInTheDocument();
        expect(screen.queryByText('Kopieren')).not.toBeInTheDocument();

        // Recovery rotate goes through the same confirmation as the normal path.
        await userEvent.click(screen.getByRole('button', { name: 'Rotieren' }));
        const rotateConfirm = await screen.findByRole('dialog', { name: 'Zugangs-Link rotieren?' });
        expect(
            within(rotateConfirm).getByText(
                'Der alte Link und alle aktiven Team-Sitzungen werden sofort ungültig. Der neue Link wird danach einmalig angezeigt.',
            ),
        ).toBeInTheDocument();

        teams.revealAccessLink.mockResolvedValueOnce({ token: 'second-token', link: LINK_V2 });
        teams.rotateAccessLink.mockResolvedValue(undefined);
        await userEvent.click(within(rotateConfirm).getByRole('button', { name: 'Rotieren' }));
        expect(teams.rotateAccessLink).toHaveBeenCalledWith('t-1', 'tm-1');
        expect(await screen.findByText(LINK_V2)).toBeInTheDocument();
    });

    it('shows an error and stays open when revoking fails', async () => {
        tournaments.get.mockResolvedValue(tournament());
        teams.list.mockResolvedValue([team()]);
        teams.revealAccessLink.mockResolvedValue({ token: 'first-token', link: LINK_V1 });
        teams.revokeAccessLink.mockRejectedValue(
            new ApiError({ code: 'NOT_FOUND', status: 404, field: null, message: 'boom' }),
        );

        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Zugangs-Link' }));
        await screen.findByText(LINK_V1);

        await userEvent.click(screen.getByRole('button', { name: 'Widerrufen' }));
        const revokeConfirm = await screen.findByRole('dialog', { name: 'Zugangs-Link widerrufen?' });
        await userEvent.click(within(revokeConfirm).getByRole('button', { name: 'Widerrufen' }));

        expect(teams.revokeAccessLink).toHaveBeenCalledWith('t-1', 'tm-1');
        // The dialog stays open (the link still works) and the failure is reported.
        expect(await screen.findByRole('alert')).toBeInTheDocument();
        expect(screen.getByText(LINK_V1)).toBeInTheDocument();
    });
});
