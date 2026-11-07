import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { PublicTournament } from '@/domain/tournament.domain.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

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
        setPublicVisibility: vi.fn(),
        listPublicTournaments: vi.fn(),
        getPublicTournament: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);

const noSession = new ApiError({
    code: 'UNAUTHENTICATED',
    status: 401,
    field: null,
    message: 'no session',
});

describe('public home', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        // A visitor without a session: the public area must still open.
        auth.refresh.mockRejectedValue(noSession);
    });

    it('lists public tournaments grouped by status in the order active, completed, published', async () => {
        const publicTournaments: PublicTournament[] = [
            { id: 'p-1', name: 'Published Cup', status: TournamentStatus.PUBLISHED, teamCount: 6 },
            { id: 'a-1', name: 'Live Open', status: TournamentStatus.ACTIVE, teamCount: 4 },
            {
                id: 'c-1',
                name: 'Finished Classic',
                status: TournamentStatus.COMPLETED,
                teamCount: 8,
            },
        ];
        tournaments.listPublicTournaments.mockResolvedValue(publicTournaments);

        renderApp('/public', { initialLocale: 'en' });

        expect(
            await screen.findByRole('heading', { name: 'Public tournaments' }),
        ).toBeInTheDocument();
        await screen.findAllByRole('listitem');

        const groupHeadings = screen
            .getAllByRole('heading', { level: 2 })
            .map((heading) => heading.textContent);
        expect(groupHeadings).toEqual(['Active', 'Completed', 'Published']);

        const entries = screen.getAllByRole('listitem').map((item) => item.textContent);
        expect(entries).toEqual([
            expect.stringContaining('Live Open'),
            expect.stringContaining('Finished Classic'),
            expect.stringContaining('Published Cup'),
        ]);
    });

    it('shows each entry with its name, status and team count', async () => {
        tournaments.listPublicTournaments.mockResolvedValue([
            { id: 'a-1', name: 'Live Open', status: TournamentStatus.ACTIVE, teamCount: 4 },
            {
                id: 'c-1',
                name: 'Finished Classic',
                status: TournamentStatus.COMPLETED,
                teamCount: 8,
            },
            { id: 'p-1', name: 'Published Cup', status: TournamentStatus.PUBLISHED, teamCount: 6 },
        ]);

        renderApp('/public', { initialLocale: 'en' });

        const [live, finished, published] = await screen.findAllByRole('listitem');
        expect(within(live!).getByText('Live Open')).toBeInTheDocument();
        expect(within(live!).getByText('Active')).toBeInTheDocument();
        expect(within(live!).getByText('Teams: 4')).toBeInTheDocument();

        expect(within(finished!).getByText('Finished Classic')).toBeInTheDocument();
        expect(within(finished!).getByText('Completed')).toBeInTheDocument();
        expect(within(finished!).getByText('Teams: 8')).toBeInTheDocument();

        expect(within(published!).getByText('Published Cup')).toBeInTheDocument();
        expect(within(published!).getByText('Published')).toBeInTheDocument();
        expect(within(published!).getByText('Teams: 6')).toBeInTheDocument();
    });

    it('links each entry to its public tournament page', async () => {
        tournaments.listPublicTournaments.mockResolvedValue([
            { id: 'a-1', name: 'Live Open', status: TournamentStatus.ACTIVE, teamCount: 4 },
        ]);

        renderApp('/public', { initialLocale: 'en' });

        const [live] = await screen.findAllByRole('listitem');
        expect(within(live!).getByRole('link', { name: 'Live Open' })).toHaveAttribute(
            'href',
            '/public/tournaments/a-1',
        );
    });

    it('shows the empty state when there are no public tournaments', async () => {
        tournaments.listPublicTournaments.mockResolvedValue([]);

        renderApp('/public', { initialLocale: 'en' });

        expect(await screen.findByText('No public tournaments yet.')).toBeInTheDocument();
        expect(screen.queryAllByRole('listitem')).toHaveLength(0);
    });
});
