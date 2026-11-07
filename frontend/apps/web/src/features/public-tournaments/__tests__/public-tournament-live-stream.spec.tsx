import { act, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { PublicRound, PublicTournamentDetail } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { FakeEventSource } from '@/test-helpers/fake-event-source.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { TournamentEventType } from '@/vocabulary/tournament-event-type.enum.ts';
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

const noSession = new ApiError({
    code: 'UNAUTHENTICATED',
    status: 401,
    field: null,
    message: 'no session',
});

function round(number: number): PublicRound {
    return {
        number,
        tables: [
            {
                tableNumber: 1,
                trio: false,
                teams: [
                    {
                        id: `falcons-${number}`,
                        name: 'Falcons',
                        players: [
                            { id: 'p1', name: 'Ann Lee' },
                            { id: 'p2', name: 'Ben Ray' },
                        ],
                    },
                    {
                        id: `owls-${number}`,
                        name: 'Owls',
                        players: [
                            { id: 'p3', name: 'Cid Moss' },
                            { id: 'p4', name: 'Dan Fox' },
                        ],
                    },
                ],
            },
        ],
    };
}

function tournament(status: TournamentStatus, rounds: PublicRound[]): PublicTournamentDetail {
    return { id: 't-1', name: 'Autumn Cup', status, rounds };
}

function eventPayload(type: string): string {
    return JSON.stringify({ type, tournamentId: 't-1', roundId: 'r-1' });
}

async function openedSource(): Promise<FakeEventSource> {
    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    return FakeEventSource.instances[0]!;
}

beforeEach(() => {
    vi.resetAllMocks();
    // A visitor without a session: the public area must still open.
    auth.refresh.mockRejectedValue(noSession);
});

describe('live stream of a public active tournament', () => {
    it('shows Live once the public stream is open and refetches the pairings when a round starts', async () => {
        tournaments.getPublicTournament
            .mockResolvedValueOnce(tournament(TournamentStatus.ACTIVE, [round(1)]))
            .mockResolvedValue(tournament(TournamentStatus.ACTIVE, [round(1), round(2)]));

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });
        await screen.findByRole('heading', { name: 'Autumn Cup' });
        expect(screen.queryByRole('region', { name: 'Round 2' })).toBeNull();

        const source = await openedSource();
        expect(source.url).toMatch(/\/api\/public\/tournaments\/t-1\/events$/);
        expect(await screen.findByText('Offline')).toBeInTheDocument();

        act(() => source.emitOpen());
        expect(await screen.findByText('Live')).toBeInTheDocument();

        act(() => source.emit(TournamentEventType.ROUND_STARTED, eventPayload('ROUND_STARTED')));

        expect(await screen.findByRole('region', { name: 'Round 2' })).toBeInTheDocument();
    });

    it('refetches on results published and closes the stream once the tournament is published', async () => {
        tournaments.getPublicTournament
            .mockResolvedValueOnce(tournament(TournamentStatus.ACTIVE, [round(1)]))
            .mockResolvedValue(tournament(TournamentStatus.PUBLISHED, [round(1)]));

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });
        await screen.findByRole('heading', { name: 'Autumn Cup' });
        const source = await openedSource();
        act(() => source.emitOpen());
        expect(await screen.findByText('Live')).toBeInTheDocument();
        const callsBeforePublish = tournaments.getPublicTournament.mock.calls.length;

        act(() =>
            source.emit(
                TournamentEventType.RESULTS_PUBLISHED,
                eventPayload(TournamentEventType.RESULTS_PUBLISHED),
            ),
        );

        await waitFor(() => expect(source.closed).toBe(true));
        expect(tournaments.getPublicTournament.mock.calls.length).toBeGreaterThan(
            callsBeforePublish,
        );
        expect(screen.queryByText('Live')).not.toBeInTheDocument();
        expect(screen.queryByText('Offline')).not.toBeInTheDocument();
    });

    it('refetches the public tournament when it finishes and shows the completed status', async () => {
        tournaments.getPublicTournament
            .mockResolvedValueOnce(tournament(TournamentStatus.ACTIVE, [round(1)]))
            .mockResolvedValue(tournament(TournamentStatus.COMPLETED, [round(1)]));

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });
        await screen.findByRole('heading', { name: 'Autumn Cup' });
        const source = await openedSource();
        act(() => source.emitOpen());
        expect(await screen.findByText('Live')).toBeInTheDocument();
        const callsBeforeFinish = tournaments.getPublicTournament.mock.calls.length;

        act(() =>
            source.emit(
                TournamentEventType.TOURNAMENT_FINISHED,
                eventPayload(TournamentEventType.TOURNAMENT_FINISHED),
            ),
        );

        expect(await screen.findByText('Completed')).toBeInTheDocument();
        expect(tournaments.getPublicTournament.mock.calls.length).toBeGreaterThan(
            callsBeforeFinish,
        );
        expect(source.closed).toBe(false);
    });

    it('refetches the pairings on every reconnect to catch up on missed rounds', async () => {
        tournaments.getPublicTournament.mockResolvedValue(
            tournament(TournamentStatus.ACTIVE, [round(1)]),
        );

        renderApp('/public/tournaments/t-1', { initialLocale: 'en' });
        await screen.findByRole('heading', { name: 'Autumn Cup' });
        const first = await openedSource();
        act(() => first.emitOpen());
        await waitFor(() => expect(tournaments.getPublicTournament).toHaveBeenCalledTimes(2));

        act(() => first.emitError());
        expect(await screen.findByText('Offline')).toBeInTheDocument();
        await waitFor(() => expect(FakeEventSource.instances).toHaveLength(2), { timeout: 3000 });
        const callsBeforeReconnect = tournaments.getPublicTournament.mock.calls.length;

        act(() => FakeEventSource.instances[1]!.emitOpen());

        await waitFor(() =>
            expect(tournaments.getPublicTournament.mock.calls.length).toBeGreaterThan(
                callsBeforeReconnect,
            ),
        );
        expect(await screen.findByText('Live')).toBeInTheDocument();
    });
});
