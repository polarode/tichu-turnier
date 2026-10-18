import type { QueryClient } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { QueryKeys } from '@/shared/constants/query-keys.ts';
import { SseClient, type ServerEventSource } from '@/shared/sse/sse-client.ts';
import { createTestQueryClient } from '@/test-helpers/render-with-providers.tsx';

/** Minimal `EventSource` double: records listeners and lets tests drive open/error/events. */
class FakeEventSource implements ServerEventSource {
    static instances: FakeEventSource[] = [];

    onopen: ((event: Event) => void) | null = null;
    onerror: ((event: Event) => void) | null = null;
    closed = false;
    readonly listeners = new Map<string, Set<(event: MessageEvent) => void>>();

    constructor(readonly url: string) {
        FakeEventSource.instances.push(this);
    }

    addEventListener(type: string, listener: (event: MessageEvent) => void): void {
        const set = this.listeners.get(type) ?? new Set();
        set.add(listener);
        this.listeners.set(type, set);
    }

    removeEventListener(type: string, listener: (event: MessageEvent) => void): void {
        this.listeners.get(type)?.delete(listener);
    }

    close(): void {
        this.closed = true;
    }

    emitOpen(): void {
        this.onopen?.(new Event('open'));
    }

    emitError(): void {
        this.onerror?.(new Event('error'));
    }

    emit(type: string, data: string): void {
        this.listeners.get(type)?.forEach((listener) => listener(new MessageEvent(type, { data })));
    }
}

describe('SseClient', () => {
    let queryClient: QueryClient;

    beforeEach(() => {
        vi.useFakeTimers();
        FakeEventSource.instances = [];
        queryClient = createTestQueryClient();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    const spyInvalidate = () => vi.spyOn(queryClient, 'invalidateQueries');

    function createClient() {
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
});
