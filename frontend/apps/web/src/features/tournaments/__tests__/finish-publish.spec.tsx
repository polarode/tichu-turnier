import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { TableConfirmation } from '@/domain/game.domain.ts';
import type { Round, TableOverview } from '@/domain/round.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

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

function tournament(status: Tournament['status'], totalRounds = 2): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

const completed: TableConfirmation = {
    status: TableStatus.COMPLETED,
    confirmedTeamIds: ['a', 'b'],
};
const playing: TableConfirmation = { status: TableStatus.PLAYING, confirmedTeamIds: [] };

function table(tableNumber: number, confirmation: TableConfirmation): TableOverview {
    const schedule = [1, 2, 3, 4].map((gameNumber) => ({
        gameNumber,
        teamIds: ['a', 'b'] as [string, string],
    }));
    return {
        tableNumber,
        teamIds: ['a', 'b'],
        trio: false,
        confirmation,
        gameCount: 4,
        schedule,
        games: [],
    };
}

/** [count] rounds; the last one has [completedTables] of [tableCount] tables completed, earlier ones are complete. */
function rounds(count: number, tableCount: number, completedTables: number): Round[] {
    return Array.from({ length: count }, (_, i) => ({
        number: i + 1,
        tables: Array.from({ length: tableCount }, (__, t) =>
            table(t + 1, i + 1 < count || t < completedTables ? completed : playing),
        ),
    }));
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue({ id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' });
    teams.list.mockResolvedValue([]);
    tournaments.standings.mockResolvedValue([]);
});

async function progressCard() {
    return screen.findByRole('region', { name: 'Fortschritt' });
}

describe('finish action in the last round', () => {
    it('is disabled with the number of open tables while a table is still open', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', 2));
        tournaments.rounds.mockResolvedValue(rounds(2, 3, 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        expect(
            await within(card).findByRole('button', { name: 'Turnier abschließen' }),
        ).toBeDisabled();
        expect(card).toHaveTextContent('Noch offene Tische: 1');
    });

    it('finishes the tournament after confirmation', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', 2));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 2));
        tournaments.finish.mockResolvedValue(tournament('COMPLETED', 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await userEvent.click(
            await within(card).findByRole('button', { name: 'Turnier abschließen' }),
        );
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Mit dem Abschluss wird die Endrangliste festgelegt.');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Bestätigen' }));

        expect(tournaments.finish).toHaveBeenCalledWith('t-1');
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    });

    it('shows the localized reason when finishing is rejected', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', 2));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 2));
        tournaments.finish.mockRejectedValue(
            new ApiError({
                code: 'ROUNDS_REMAINING',
                status: 409,
                field: null,
                message: 'rounds remaining',
            }),
        );
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await userEvent.click(
            await within(card).findByRole('button', { name: 'Turnier abschließen' }),
        );
        await userEvent.click(
            within(await screen.findByRole('dialog')).getByRole('button', { name: 'Bestätigen' }),
        );

        expect(
            await within(card).findByText(
                'Das Turnier kann erst nach der letzten Runde abgeschlossen werden.',
            ),
        ).toBeInTheDocument();
    });
});

describe('publish action once completed', () => {
    it('publishes the results after confirmation', async () => {
        tournaments.get.mockResolvedValue(tournament('COMPLETED', 4));
        tournaments.rounds.mockResolvedValue(rounds(4, 2, 2));
        tournaments.publish.mockResolvedValue(tournament('PUBLISHED', 4));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await userEvent.click(
            await within(card).findByRole('button', { name: 'Ergebnisse veröffentlichen' }),
        );
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Danach sehen die Teams die Endrangliste.');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Bestätigen' }));

        expect(tournaments.publish).toHaveBeenCalledWith('t-1');
    });

    it('shows the note and no buttons once published', async () => {
        tournaments.get.mockResolvedValue(tournament('PUBLISHED', 4));
        tournaments.rounds.mockResolvedValue(rounds(4, 2, 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        expect(await within(card).findByText('Ergebnisse veröffentlicht')).toBeInTheDocument();
        expect(within(card).queryByRole('button')).not.toBeInTheDocument();
    });

    it('labels the standings tab as the Final Ranking once completed', async () => {
        tournaments.get.mockResolvedValue(tournament('COMPLETED', 4));
        tournaments.rounds.mockResolvedValue(rounds(4, 2, 2));
        renderApp('/admin/tournaments/t-1');

        expect(await screen.findByRole('tab', { name: 'Endrangliste' })).toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Rangliste' })).not.toBeInTheDocument();
    });
});
