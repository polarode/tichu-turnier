import { focusManager } from '@tanstack/react-query';
import { act, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import type { TeamTable } from '@/domain/game.domain.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { teamFinalRankingQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentEventType } from '@/vocabulary/tournament-event-type.enum.ts';
import { FakeEventSource } from '@/test-helpers/fake-event-source.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/team-auth-api/team-auth.repository.ts', () => ({
    TeamAuthRepository: {
        exchange: vi.fn(),
        me: vi.fn(),
        finalRanking: vi.fn(),
    },
}));

vi.mock('@/scoring-api/scoring.repository.ts', () => ({
    ScoringRepository: {
        currentTable: vi.fn(),
        games: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        confirm: vi.fn(),
        retract: vi.fn(),
    },
}));

const me = vi.mocked(TeamAuthRepository.me);
const finalRanking = vi.mocked(TeamAuthRepository.finalRanking);
const currentTable = vi.mocked(ScoringRepository.currentTable);
const games = vi.mocked(ScoringRepository.games);

const overview = {
    teamId: 'team-1',
    tournamentId: 't-1',
    tournamentName: 'Spring Classic',
    tournamentStatus: TournamentStatus.ACTIVE,
    teamName: 'Team Alpha',
    players: [
        { id: 'p-1', name: 'Alice' },
        { id: 'p-2', name: 'Bob' },
    ],
};

function seatedTable(): TeamTable {
    return {
        tournamentId: 't-1',
        roundNumber: 1,
        tableNumber: 2,
        confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
        grandTichuBonus: 200,
        doubleWinBonus: 200,
        teams: [
            {
                teamId: 'team-1',
                teamName: 'Team Alpha',
                players: [
                    { playerId: 'p-1', name: 'Alice' },
                    { playerId: 'p-2', name: 'Bob' },
                ],
            },
            {
                teamId: 'team-2',
                teamName: 'Team Beta',
                players: [
                    { playerId: 'p-3', name: 'Carol' },
                    { playerId: 'p-4', name: 'Dave' },
                ],
            },
        ],
        trio: false,
        schedule: [1, 2, 3, 4].map((gameNumber) => ({ gameNumber, teamIds: ['team-1', 'team-2'] })),
    };
}

function eventPayload(type: string): string {
    return JSON.stringify({ type, tournamentId: 't-1', roundId: null, tableId: null });
}

const finalRows = [
    {
        rank: 1,
        teamId: 'team-2',
        teamName: 'Team Beta',
        gamesPlayed: 12,
        victoryPoints: 9,
        tichuPoints: 450,
    },
    {
        rank: 2,
        teamId: 'team-1',
        teamName: 'Team Alpha',
        gamesPlayed: 12,
        victoryPoints: 7,
        tichuPoints: 300,
    },
];

async function openedSource(): Promise<FakeEventSource> {
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    return FakeEventSource.instances[0]!;
}

function callCounts() {
    return {
        me: me.mock.calls.length,
        table: currentTable.mock.calls.length,
        games: games.mock.calls.length,
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    me.mockReset();
    finalRanking.mockReset();
    currentTable.mockReset();
    games.mockReset();
    games.mockResolvedValue([]);
    currentTable.mockResolvedValue(seatedTable());
});

afterEach(() => {
    vi.useRealTimers();
    focusManager.setFocused(undefined);
});

describe('/team live stream', () => {
    it('opens the stream at /api/team/events while in setup and shows Live or Offline', async () => {
        me.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.SETUP });

        renderApp('/team', { initialLocale: 'en' });

        const source = await openedSource();
        expect(source.url).toMatch(/\/api\/team\/events$/);
        expect(await screen.findByText('Offline')).toBeInTheDocument();

        act(() => source.emitOpen());
        expect(await screen.findByText('Live')).toBeInTheDocument();
        expect(screen.queryByText('Offline')).not.toBeInTheDocument();

        act(() => source.emitError());
        expect(await screen.findByText('Offline')).toBeInTheDocument();
    });

    it('does not open a stream once the tournament is published', async () => {
        me.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.PUBLISHED });
        finalRanking.mockResolvedValue(finalRows);

        renderApp('/team', { initialLocale: 'en' });

        expect(await screen.findByRole('table', { name: 'Final Ranking' })).toBeInTheDocument();
        expect(FakeEventSource.instances).toHaveLength(0);
        expect(screen.queryByText('Live')).not.toBeInTheDocument();
        expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    });

    it('refetches the overview and the current table on TOURNAMENT_STARTED', async () => {
        me.mockResolvedValue(overview);
        renderApp('/team', { initialLocale: 'en' });
        const source = await openedSource();
        await screen.findByText('Team Alpha');
        await waitFor(() => expect(currentTable).toHaveBeenCalledTimes(1));
        const before = callCounts();

        act(() =>
            source.emit(
                TournamentEventType.TOURNAMENT_STARTED,
                eventPayload(TournamentEventType.TOURNAMENT_STARTED),
            ),
        );

        await waitFor(() => expect(me.mock.calls.length).toBeGreaterThan(before.me));
        await waitFor(() => expect(currentTable.mock.calls.length).toBeGreaterThan(before.table));
    });

    it('refetches the overview on RESULTS_PUBLISHED and shows the Final Ranking once published, then closes the stream', async () => {
        me.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.COMPLETED });
        renderApp('/team', { initialLocale: 'en' });
        const source = await openedSource();
        await screen.findByText('Team Alpha');
        const before = callCounts();

        me.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.PUBLISHED });
        finalRanking.mockResolvedValue(finalRows);
        act(() =>
            source.emit(
                TournamentEventType.RESULTS_PUBLISHED,
                eventPayload(TournamentEventType.RESULTS_PUBLISHED),
            ),
        );

        expect(await screen.findByRole('table', { name: 'Final Ranking' })).toBeInTheDocument();
        expect(me.mock.calls.length).toBeGreaterThan(before.me);
        expect(
            within(screen.getByRole('table', { name: 'Final Ranking' })).getByText('Team Beta'),
        ).toBeInTheDocument();
        expect(source.closed).toBe(true);
        expect(screen.queryByText('Live')).not.toBeInTheDocument();
    });

    it('refetches everything the team page shows once on a reconnect', async () => {
        me.mockResolvedValue(overview);
        const { queryClient } = renderApp('/team', { initialLocale: 'en' });
        const first = await openedSource();
        await waitFor(() => expect(currentTable).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(games).toHaveBeenCalledTimes(1));
        act(() => first.emitOpen());
        await waitFor(() => expect(me.mock.calls.length).toBeGreaterThan(1));
        await waitFor(() => expect(currentTable.mock.calls.length).toBeGreaterThan(1));
        await waitFor(() => expect(games.mock.calls.length).toBeGreaterThan(1));
        await act(() => new Promise((resolve) => setTimeout(resolve, 0)));
        const afterOpen = callCounts();

        // The final ranking is only enabled once published, so it is not observed as a
        // fetch here; its invalidation on reconnect is observed on the query client.
        const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

        act(() => first.emitError());
        await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2), { timeout: 3000 });
        act(() => FakeEventSource.instances[1]!.emitOpen());

        await waitFor(() => expect(me.mock.calls.length).toBe(afterOpen.me + 1));
        await waitFor(() => expect(currentTable.mock.calls.length).toBe(afterOpen.table + 1));
        await waitFor(() => expect(games.mock.calls.length).toBe(afterOpen.games + 1));
        expect(invalidate).toHaveBeenCalledWith({ queryKey: teamFinalRankingQueryKey() });
        await act(() => new Promise((resolve) => setTimeout(resolve, 0)));
        expect(me.mock.calls.length).toBe(afterOpen.me + 1);
        expect(currentTable.mock.calls.length).toBe(afterOpen.table + 1);
        expect(games.mock.calls.length).toBe(afterOpen.games + 1);
        expect(finalRanking).not.toHaveBeenCalled();
    });

    it('refetches the overview when the window regains focus after the data went stale', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        me.mockResolvedValue(overview);
        renderApp('/team', { initialLocale: 'en' });
        await openedSource();
        await screen.findByText('Team Alpha');

        // The overview is fresh for 30 s; age it past that so focus has something to refetch.
        await act(() => vi.advanceTimersByTimeAsync(31_000));
        const aged = me.mock.calls.length;
        act(() => focusManager.setFocused(false));
        act(() => focusManager.setFocused(true));

        await waitFor(() => expect(me.mock.calls.length).toBe(aged + 1));
    });
});
