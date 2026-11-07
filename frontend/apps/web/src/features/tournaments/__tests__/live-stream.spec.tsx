import { focusManager } from '@tanstack/react-query';
import { act, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { FakeEventSource } from '@/test-helpers/fake-event-source.ts';
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

function tournament(status: Tournament['status']): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 4 },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function eventPayload(type: string): string {
    return JSON.stringify({ type, tournamentId: 't-1', roundId: 'r-1', tableId: 'tb-1' });
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue({ id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' });
    teams.list.mockResolvedValue([]);
    tournaments.rounds.mockResolvedValue([]);
    tournaments.standings.mockResolvedValue([]);
});

afterEach(() => {
    vi.useRealTimers();
    focusManager.setFocused(undefined);
});

function callCounts() {
    return {
        tournament: tournaments.get.mock.calls.length,
        rounds: tournaments.rounds.mock.calls.length,
        standings: tournaments.standings.mock.calls.length,
    };
}

async function openedSource(): Promise<FakeEventSource> {
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    return FakeEventSource.instances[0]!;
}

describe('live stream of an active tournament', () => {
    beforeEach(() => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
    });

    it('shows Live while the stream is open and Offline otherwise', async () => {
        renderApp('/admin/tournaments/t-1', { initialLocale: 'en' });

        const source = await openedSource();
        expect(source.url).toMatch(/\/api\/tournaments\/t-1\/events$/);
        expect(await screen.findByText('Offline')).toBeInTheDocument();

        act(() => source.emitOpen());
        expect(await screen.findByText('Live')).toBeInTheDocument();
        expect(screen.queryByText('Offline')).not.toBeInTheDocument();

        act(() => source.emitError());
        expect(await screen.findByText('Offline')).toBeInTheDocument();
    });

    it.each(['GAME_RECORDED', 'TABLE_CONFIRMED', 'CONFIRMATION_RETRACTED'])(
        'refetches rounds and standings on %s',
        async (type) => {
            renderApp('/admin/tournaments/t-1?tab=standings', { initialLocale: 'en' });
            const source = await openedSource();
            await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(1));
            const roundsBefore = tournaments.rounds.mock.calls.length;

            act(() => source.emit(type, eventPayload(type)));

            await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(2));
            expect(tournaments.rounds.mock.calls.length).toBeGreaterThan(roundsBefore);
        },
    );

    it('refetches tournament, teams, rounds and standings on every open, including a reconnect', async () => {
        renderApp('/admin/tournaments/t-1?tab=standings', { initialLocale: 'en' });
        const first = await openedSource();
        await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(1));
        const calls = () => [
            tournaments.get.mock.calls.length,
            teams.list.mock.calls.length,
            tournaments.rounds.mock.calls.length,
            tournaments.standings.mock.calls.length,
        ];
        const initial = calls();

        act(() => first.emitOpen());
        await waitFor(() =>
            calls().forEach((count, i) => expect(count).toBeGreaterThan(initial[i]!)),
        );
        const afterOpen = calls();

        act(() => first.emitError());
        await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2), { timeout: 3000 });
        act(() => FakeEventSource.instances[1]!.emitOpen());
        await waitFor(() =>
            calls().forEach((count, i) => expect(count).toBeGreaterThan(afterOpen[i]!)),
        );
    });

    it.each(['TOURNAMENT_STARTED', 'ROUND_STARTED', 'TOURNAMENT_FINISHED'])(
        'refetches tournament, rounds and standings on %s',
        async (type) => {
            renderApp('/admin/tournaments/t-1?tab=standings', { initialLocale: 'en' });
            const source = await openedSource();
            await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(1));
            const before = callCounts();

            act(() => source.emit(type, eventPayload(type)));

            await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(2));
            expect(tournaments.get.mock.calls.length).toBeGreaterThan(before.tournament);
            expect(tournaments.rounds.mock.calls.length).toBeGreaterThan(before.rounds);
        },
    );

    it.each(['TOTAL_ROUNDS_CHANGED', 'RESULTS_PUBLISHED'])(
        'refetches only the tournament on %s',
        async (type) => {
            renderApp('/admin/tournaments/t-1?tab=standings', { initialLocale: 'en' });
            const source = await openedSource();
            await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(1));
            const before = callCounts();

            act(() => source.emit(type, eventPayload(type)));

            await waitFor(() =>
                expect(tournaments.get.mock.calls.length).toBeGreaterThan(before.tournament),
            );
            await act(() => new Promise((resolve) => setTimeout(resolve, 0)));
            expect(tournaments.rounds.mock.calls.length).toBe(before.rounds);
            expect(tournaments.standings.mock.calls.length).toBe(before.standings);
        },
    );

    it('does not poll rounds or standings while the tournament is active', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
        renderApp('/admin/tournaments/t-1?tab=standings', { initialLocale: 'en' });
        await openedSource();
        await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(1));
        const before = callCounts();

        await act(() => vi.advanceTimersByTimeAsync(60_000));

        expect(tournaments.rounds.mock.calls.length).toBe(before.rounds);
        expect(tournaments.standings.mock.calls.length).toBe(before.standings);
    });

    it('refetches tournament, rounds and standings when the window regains focus', async () => {
        renderApp('/admin/tournaments/t-1?tab=standings', { initialLocale: 'en' });
        await openedSource();
        await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(1));
        const before = callCounts();

        act(() => focusManager.setFocused(false));
        act(() => focusManager.setFocused(true));

        await waitFor(() => expect(tournaments.standings).toHaveBeenCalledTimes(2));
        expect(tournaments.get.mock.calls.length).toBeGreaterThan(before.tournament);
        expect(tournaments.rounds.mock.calls.length).toBeGreaterThan(before.rounds);
    });

    it('closes the stream when leaving the page', async () => {
        const view = renderApp('/admin/tournaments/t-1', { initialLocale: 'en' });
        const source = await openedSource();

        view.unmount();

        expect(source.closed).toBe(true);
    });
});

describe.each(['SETUP', 'COMPLETED'] as const)('a %s tournament', (status) => {
    it('opens no stream and shows no badge', async () => {
        tournaments.get.mockResolvedValue(tournament(status));
        renderApp('/admin/tournaments/t-1', { initialLocale: 'en' });

        expect(await screen.findByText('Jahresturnier')).toBeInTheDocument();
        expect(FakeEventSource.instances).toHaveLength(0);
        expect(screen.queryByText('Live')).not.toBeInTheDocument();
        expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    });
});
