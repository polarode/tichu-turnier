import { act, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { TeamTable } from '@/domain/game.domain.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
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

async function openedSource(): Promise<FakeEventSource> {
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    return FakeEventSource.instances[0]!;
}

beforeEach(() => {
    vi.clearAllMocks();
    me.mockReset();
    currentTable.mockReset();
    games.mockReset();
    games.mockResolvedValue([]);
    currentTable.mockResolvedValue(seatedTable());
    me.mockResolvedValue(overview);
});

afterEach(() => {
    vi.useRealTimers();
});

describe('/team/score live stream', () => {
    it('opens the stream at /api/team/events and shows Live once it opens', async () => {
        renderApp('/team/score', { initialLocale: 'en' });

        const source = await openedSource();
        expect(source.url).toMatch(/\/api\/team\/events$/);
        expect(await screen.findByText('Offline')).toBeInTheDocument();

        act(() => source.emitOpen());
        expect(await screen.findByText('Live')).toBeInTheDocument();
        expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    });

    it('refetches the current table on ROUND_STARTED', async () => {
        renderApp('/team/score', { initialLocale: 'en' });
        const source = await openedSource();
        // The table is on screen once its games are in too; the baseline is taken after the mount fetches.
        await screen.findAllByRole('tab');
        const before = currentTable.mock.calls.length;

        act(() =>
            source.emit(
                TournamentEventType.ROUND_STARTED,
                eventPayload(TournamentEventType.ROUND_STARTED),
            ),
        );

        await waitFor(() => expect(currentTable.mock.calls.length).toBeGreaterThan(before));
    });

    it('does not poll the current table or the games on an interval', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        renderApp('/team/score', { initialLocale: 'en' });
        await openedSource();
        await waitFor(() => expect(games).toHaveBeenCalledTimes(1));
        const tableFetches = currentTable.mock.calls.length;
        const gameFetches = games.mock.calls.length;

        await act(() => vi.advanceTimersByTimeAsync(120_000));

        expect(currentTable.mock.calls.length).toBe(tableFetches);
        expect(games.mock.calls.length).toBe(gameFetches);
    });
});
