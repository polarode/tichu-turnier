import type { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { QueryKeys } from '@/shared/constants/query-keys.ts';
import { SseClient } from '@/shared/sse/sse-client.ts';
import { FakeEventSource } from '@/test-helpers/fake-event-source.ts';
import { createTestQueryClient } from '@/test-helpers/render-with-providers.tsx';

describe('SseClient', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        vi.useFakeTimers();
        queryClient = createTestQueryClient();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    const spyInvalidate = () => vi.spyOn(queryClient, 'invalidateQueries');

    function createClient(onStateChange?: (connected: boolean) => void) {
        return new SseClient({
            url: '/api/tournaments/t-1/events',
            queryClient,
            subscriptions: [
                { event: 'tournament.changed', queryKeys: [[QueryKeys.TOURNAMENTS]] },
                {
                    event: 'team.changed',
                    queryKeys: [[QueryKeys.TEAMS], [QueryKeys.TOURNAMENTS]],
                },
            ],
            eventSourceFactory: (url) => new FakeEventSource(url),
            initialRetryDelayMs: 1000,
            maxRetryDelayMs: 4000,
            onStateChange,
        });
    }

    it('opens the stream and invalidates the query keys of each event', () => {
        const invalidate = spyInvalidate();
        const client = createClient();
        client.connect();

        expect(FakeEventSource.instances).toHaveLength(1);
        const source = FakeEventSource.instances[0]!;
        expect(source.url).toBe('/api/tournaments/t-1/events');

        source.emit('tournament.changed', '{}');
        expect(invalidate).toHaveBeenCalledWith({ queryKey: [QueryKeys.TOURNAMENTS] });

        invalidate.mockClear();
        source.emit('team.changed', '{}');
        expect(invalidate).toHaveBeenCalledTimes(2);
        expect(invalidate).toHaveBeenCalledWith({ queryKey: [QueryKeys.TEAMS] });
        expect(invalidate).toHaveBeenCalledWith({ queryKey: [QueryKeys.TOURNAMENTS] });
    });

    it('reconnects with exponential backoff after the connection drops', () => {
        const client = createClient();
        client.connect();

        const first = FakeEventSource.instances[0]!;
        first.emitError();
        expect(first.closed).toBe(true);

        vi.advanceTimersByTime(999);
        expect(FakeEventSource.instances).toHaveLength(1);
        vi.advanceTimersByTime(1);
        expect(FakeEventSource.instances).toHaveLength(2);

        // The second drop waits twice as long (2000ms).
        FakeEventSource.instances[1]!.emitError();
        vi.advanceTimersByTime(1999);
        expect(FakeEventSource.instances).toHaveLength(2);
        vi.advanceTimersByTime(1);
        expect(FakeEventSource.instances).toHaveLength(3);
    });

    it('resets the backoff after a successful reconnect', () => {
        const client = createClient();
        client.connect();

        FakeEventSource.instances[0]!.emitError();
        vi.advanceTimersByTime(1000);
        const second = FakeEventSource.instances[1]!;
        second.emitOpen();
        second.emitError();

        vi.advanceTimersByTime(1000);
        expect(FakeEventSource.instances).toHaveLength(3);
    });

    it('stops reconnecting and detaches listeners on disconnect', () => {
        const invalidate = spyInvalidate();
        const client = createClient();
        client.connect();

        const source = FakeEventSource.instances[0]!;
        client.disconnect();

        expect(source.closed).toBe(true);
        expect(client.isConnected).toBe(false);

        source.emitError();
        source.emit('tournament.changed', '{}');
        vi.advanceTimersByTime(30_000);

        expect(FakeEventSource.instances).toHaveLength(1);
        expect(invalidate).not.toHaveBeenCalled();
    });

    it('reports connected on every open and disconnected on error', () => {
        const states: boolean[] = [];
        const client = createClient((connected) => states.push(connected));
        client.connect();
        expect(states).toEqual([]);

        FakeEventSource.instances[0]!.emitOpen();
        FakeEventSource.instances[0]!.emitError();
        vi.advanceTimersByTime(1000);
        FakeEventSource.instances[1]!.emitOpen();

        expect(states).toEqual([true, false, true]);
    });

    it('reports disconnected on disconnect only after having been connected', () => {
        const states: boolean[] = [];
        const client = createClient((connected) => states.push(connected));
        client.connect();
        client.disconnect();
        expect(states).toEqual([]);

        client.connect();
        FakeEventSource.instances[1]!.emitOpen();
        client.disconnect();
        expect(states).toEqual([true, false]);

        vi.advanceTimersByTime(30_000);
        FakeEventSource.instances[1]!.emitError();
        expect(states).toEqual([true, false]);
    });
});
