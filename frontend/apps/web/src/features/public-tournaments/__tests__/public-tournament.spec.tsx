import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { PublicTournamentDetail } from '@/domain/tournament.domain.ts';
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

/** Two rounds; round 2 carries the trio table (Owls, Pagoda, Comet). */
const autumnCup: PublicTournamentDetail = {
    id: 't-1',
    name: 'Autumn Cup',
    status: TournamentStatus.ACTIVE,
    rounds: [
        {
            number: 1,
            tables: [
                {
                    tableNumber: 1,
                    trio: false,
                    teams: [
                        {
                            id: 'falcons',
                            name: 'Falcons',
                            players: [
                                { id: 'p1', name: 'Ann Lee' },
                                { id: 'p2', name: 'Ben Ray' },
                            ],
                        },
                        {
                            id: 'owls',
                            name: 'Owls',
                            players: [
                                { id: 'p3', name: 'Cid Moss' },
                                { id: 'p4', name: 'Dan Fox' },
                            ],
                        },
                    ],
                },
                {
                    tableNumber: 2,
                    trio: false,
                    teams: [
                        {
                            id: 'star',
                            name: 'Star',
                            players: [
                                { id: 'p5', name: 'Eva Dunn' },
                                { id: 'p6', name: 'Finn Gray' },
                            ],
                        },
                        {
                            id: 'pagoda',
                            name: 'Pagoda',
                            players: [
                                { id: 'p7', name: 'Gus Hale' },
                                { id: 'p8', name: 'Hana Ito' },
                            ],
                        },
                    ],
                },
            ],
        },
        {
            number: 2,
            tables: [
                {
                    tableNumber: 1,
                    trio: false,
                    teams: [
                        {
                            id: 'falcons',
                            name: 'Falcons',
                            players: [
                                { id: 'p1', name: 'Ann Lee' },
                                { id: 'p2', name: 'Ben Ray' },
                            ],
                        },
                        {
                            id: 'star',
                            name: 'Star',
                            players: [
                                { id: 'p5', name: 'Eva Dunn' },
                                { id: 'p6', name: 'Finn Gray' },
                            ],
                        },
                    ],
                },
                {
                    tableNumber: 2,
                    trio: true,
                    teams: [
                        {
                            id: 'owls',
                            name: 'Owls',
                            players: [
                                { id: 'p3', name: 'Cid Moss' },
                                { id: 'p4', name: 'Dan Fox' },
                            ],
                        },
                        {
                            id: 'pagoda',
                            name: 'Pagoda',
                            players: [
                                { id: 'p7', name: 'Gus Hale' },
                                { id: 'p8', name: 'Hana Ito' },
                            ],
                        },
                        {
                            id: 'comet',
                            name: 'Comet',
                            players: [
                                { id: 'p9', name: 'Ivy Kent' },
                                { id: 'p10', name: 'Jon Lowe' },
                            ],
                        },
                    ],
                },
            ],
        },
    ],
};

describe('public tournament page', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        // A visitor without a session: the public area must still open.
        auth.refresh.mockRejectedValue(noSession);
    });

    it('shows every round newest first, with its tables, trio marker, teams and players', async () => {
        tournaments.getPublicTournament.mockResolvedValue(autumnCup);

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });

        expect(await screen.findByRole('heading', { name: 'Autumn Cup' })).toBeInTheDocument();

        const roundHeadings = screen
            .getAllByRole('heading', { level: 2 })
            .map((heading) => heading.textContent);
        expect(roundHeadings).toEqual(['Round 2', 'Round 1']);

        const round2 = screen.getByRole('region', { name: 'Round 2' });
        const round1 = screen.getByRole('region', { name: 'Round 1' });

        const round2Tables = within(round2).getAllByRole('article');
        expect(
            round2Tables.map((card) => within(card).getByRole('heading', { level: 3 }).textContent),
        ).toEqual(['Table A', 'Table B']);

        const round1Tables = within(round1).getAllByRole('article');
        expect(
            round1Tables.map((card) => within(card).getByRole('heading', { level: 3 }).textContent),
        ).toEqual(['Table A', 'Table B']);

        const trioCard = round2Tables[1]!;
        expect(within(trioCard).getByText('Trio')).toBeInTheDocument();
        expect(within(trioCard).getByText('Owls')).toBeInTheDocument();
        expect(within(trioCard).getByText('Cid Moss · Dan Fox')).toBeInTheDocument();
        expect(within(trioCard).getByText('Pagoda')).toBeInTheDocument();
        expect(within(trioCard).getByText('Gus Hale · Hana Ito')).toBeInTheDocument();
        expect(within(trioCard).getByText('Comet')).toBeInTheDocument();
        expect(within(trioCard).getByText('Ivy Kent · Jon Lowe')).toBeInTheDocument();

        const normalCard = round2Tables[0]!;
        expect(within(normalCard).queryByText('Trio')).toBeNull();
        expect(within(normalCard).getByText('Falcons')).toBeInTheDocument();
        expect(within(normalCard).getByText('Ann Lee · Ben Ray')).toBeInTheDocument();
        expect(within(normalCard).getByText('Star')).toBeInTheDocument();
        expect(within(normalCard).getByText('Eva Dunn · Finn Gray')).toBeInTheDocument();

        expect(screen.getAllByText('Trio')).toHaveLength(1);
        expect(within(round1Tables[1]!).getByText('Pagoda')).toBeInTheDocument();
        expect(within(round1Tables[1]!).getByText('Gus Hale · Hana Ito')).toBeInTheDocument();
    });

    it('shows no results or standings', async () => {
        tournaments.getPublicTournament.mockResolvedValue(autumnCup);

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });

        await screen.findByRole('heading', { name: 'Autumn Cup' });
        expect(screen.queryByRole('heading', { name: /standings|ranking/i })).toBeNull();
        expect(screen.queryByText(/victory points|\bVP\b/i)).toBeNull();
    });

    it('shows a not-found alert with a link back to the public home for a 404', async () => {
        tournaments.getPublicTournament.mockRejectedValue(
            new ApiError({ code: 'NOT_FOUND', status: 404, field: null, message: 'not found' }),
        );

        renderApp('/public/tournaments/t-9', { initialLocale: 'en' });

        const alert = await screen.findByRole('alert');
        expect(alert).toHaveTextContent('could not be found');
        expect(screen.getByRole('link', { name: 'Back to public tournaments' })).toHaveAttribute(
            'href',
            '/public',
        );
    });
});
