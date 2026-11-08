import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { PublicTournamentDetail } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { publicTournamentDetailDtoSchema } from '@/tournament-api/dtos/public-tournament.dto.ts';
import { publicTournamentDetailDtoToDomain } from '@/tournament-api/mappers/public-tournament.mapper.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { bodyRows } from '@/test-helpers/table-rows.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: { register: vi.fn(), login: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}));
vi.mock('@/tournament-api/tournament.repository.ts', async () => {
    const { mockTournamentRepositoryModule } =
        await import('@/test-helpers/tournament-repository.mock.ts');
    return mockTournamentRepositoryModule();
});

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);

/**
 * The shared contract fixture (a Published tournament): the backend asserts its API answers
 * exactly `response`; here the same wire data runs through the real zod schema and mapper.
 */
const publishedFixture = JSON.parse(
    readFileSync(
        resolve(
            __dirname,
            '../../../../../../../contract-fixtures/public-tournament-published.json',
        ),
        'utf8',
    ),
) as { response: unknown };
const harvestCup = publicTournamentDetailDtoToDomain(
    publicTournamentDetailDtoSchema.parse(publishedFixture.response),
);

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

/** A round's table, found through its heading. */
function tableItem(round: HTMLElement, heading: string): HTMLElement {
    return within(round).getByRole('heading', { level: 3, name: heading }).closest('li')!;
}

describe('public tournament page', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        // A visitor without a session: the public area must still open.
        auth.refresh.mockRejectedValue(noSession);
    });

    it('opens on the newest round and switches rounds through the selector', async () => {
        tournaments.getPublicTournament.mockResolvedValue(autumnCup);
        const user = userEvent.setup();

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });

        expect(await screen.findByRole('heading', { name: 'Autumn Cup' })).toBeInTheDocument();

        const selector = screen.getByRole('tablist', { name: 'Select round' });
        expect(
            within(selector)
                .getAllByRole('tab')
                .map((tab) => tab.textContent),
        ).toEqual(['Round 1', 'Round 2']);
        expect(screen.getByRole('tab', { name: 'Round 2' })).toHaveAttribute(
            'aria-selected',
            'true',
        );
        expect(screen.queryByRole('region', { name: 'Round 1' })).toBeNull();

        const round2 = screen.getByRole('region', { name: 'Round 2' });
        expect(
            within(round2)
                .getAllByRole('heading', { level: 3 })
                .map((heading) => heading.textContent),
        ).toEqual(['Table A', 'Table B']);

        const trioTable = tableItem(round2, 'Table B');
        expect(within(trioTable).getByText('Trio')).toBeInTheDocument();
        expect(within(trioTable).getByText('Owls')).toBeInTheDocument();
        expect(within(trioTable).getByText('Cid Moss · Dan Fox')).toBeInTheDocument();
        expect(within(trioTable).getByText('Pagoda')).toBeInTheDocument();
        expect(within(trioTable).getByText('Gus Hale · Hana Ito')).toBeInTheDocument();
        expect(within(trioTable).getByText('Comet')).toBeInTheDocument();
        expect(within(trioTable).getByText('Ivy Kent · Jon Lowe')).toBeInTheDocument();

        const normalTable = tableItem(round2, 'Table A');
        expect(within(normalTable).queryByText('Trio')).toBeNull();
        expect(within(normalTable).getByText('Falcons')).toBeInTheDocument();
        expect(within(normalTable).getByText('Ann Lee · Ben Ray')).toBeInTheDocument();
        expect(within(normalTable).getByText('Star')).toBeInTheDocument();
        expect(within(normalTable).getByText('Eva Dunn · Finn Gray')).toBeInTheDocument();

        await user.click(screen.getByRole('tab', { name: 'Round 1' }));

        const round1 = screen.getByRole('region', { name: 'Round 1' });
        expect(screen.queryByRole('region', { name: 'Round 2' })).toBeNull();
        expect(within(round1).queryByText('Trio')).toBeNull();
        expect(within(tableItem(round1, 'Table B')).getByText('Pagoda')).toBeInTheDocument();
        expect(
            within(tableItem(round1, 'Table B')).getByText('Gus Hale · Hana Ito'),
        ).toBeInTheDocument();
    });

    it('shows a single round without a selector', async () => {
        tournaments.getPublicTournament.mockResolvedValue({
            ...autumnCup,
            rounds: autumnCup.rounds.slice(0, 1),
        });

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });

        expect(await screen.findByRole('region', { name: 'Round 1' })).toBeInTheDocument();
        expect(screen.queryByRole('tablist', { name: 'Select round' })).toBeNull();
    });

    it.each([TournamentStatus.ACTIVE, TournamentStatus.COMPLETED])(
        'shows no results, standings or table totals while the tournament is %s',
        async (status) => {
            tournaments.getPublicTournament.mockResolvedValue({ ...autumnCup, status });

            renderApp('/public/tournaments/t-1', { initialLocale: 'en' });

            await screen.findByRole('heading', { name: 'Autumn Cup' });
            expect(screen.queryByRole('heading', { name: /standings|ranking/i })).toBeNull();
            expect(screen.queryByText(/victory points|\bVP\b|\bTP\b/i)).toBeNull();
        },
    );

    it('shows the Final Ranking first and each team totals per table on the Rounds tab once published', async () => {
        tournaments.getPublicTournament.mockResolvedValue(harvestCup);
        const user = userEvent.setup();

        renderApp('/public/tournaments/t-harvest-cup', { initialLocale: 'en' });

        await screen.findByRole('heading', { name: 'Harvest Cup' });

        const ranking = screen.getByRole('table', { name: 'Final Ranking' });
        expect(bodyRows(ranking).map(([team, rank]) => [team, rank])).toEqual([
            ['Falcons', '1'],
            ['Ravens', '2'],
            ['Badgers', '3'],
            ['Owls', '3'],
            ['Wolves', '5'],
        ]);

        await user.click(screen.getByRole('tab', { name: 'Rounds' }));

        const round2 = screen.getByRole('region', { name: 'Round 2' });
        const falcons = within(tableItem(round2, 'Table A')).getByText('Falcons').closest('li')!;
        expect(falcons).toHaveTextContent('8 VP 240 TP');

        await user.click(screen.getByRole('tab', { name: 'Round 1' }));

        const round1 = screen.getByRole('region', { name: 'Round 1' });
        const wolves = within(tableItem(round1, 'Table B')).getByText('Wolves').closest('li')!;
        expect(wolves).toHaveTextContent('0 VP 120 TP');
    });

    it('shows no Final Ranking before the tournament is published', async () => {
        tournaments.getPublicTournament.mockResolvedValue(autumnCup);

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });

        await screen.findByRole('heading', { name: 'Autumn Cup' });
        expect(screen.queryByRole('table', { name: 'Final Ranking' })).toBeNull();
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
