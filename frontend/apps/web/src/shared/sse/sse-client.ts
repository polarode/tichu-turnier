import type { QueryClient, QueryKey } from '@tanstack/react-query';

import { resolveApiUrl } from '@/shared/api/api-client.ts';

/**
 * The subset of the browser `EventSource` the client relies on, so tests can supply a
 * fake and so a future transport can be swapped without touching the client.
 */
export interface ServerEventSource {
    onopen: ((event: Event) => void) | null;
    onerror: ((event: Event) => void) | null;
    addEventListener(type: string, listener: (event: MessageEvent) => void): void;
    removeEventListener(type: string, listener: (event: MessageEvent) => void): void;
    close(): void;
}

/** Binds one stream event name to the query keys it should invalidate. */
export interface SseSubscription {
    /** SSE event name; `'message'` is the default stream event. */
    event: string;
    /** Centralized query keys to invalidate when the event arrives. */
    queryKeys: readonly QueryKey[];
}

export interface SseClientOptions {
    /** Stream path or URL; a relative path is resolved against the API base. */
    url: string;
    queryClient: Pick<QueryClient, 'invalidateQueries'>;
    subscriptions: readonly SseSubscription[];
    /** Injectable transport; defaults to a credential-carrying `EventSource`. */
    eventSourceFactory?: (url: string) => ServerEventSource;
    /** First reconnect delay; doubles on every failed attempt. */
    initialRetryDelayMs?: number;
    /** Upper bound for the exponential backoff. */
    maxRetryDelayMs?: number;
}

const DEFAULT_INITIAL_RETRY_DELAY_MS = 1_000;
const DEFAULT_MAX_RETRY_DELAY_MS = 30_000;

function defaultEventSourceFactory(url: string): ServerEventSource {
    return new EventSource(resolveApiUrl(url), { withCredentials: true });
}

/**
 * A reconnecting Server-Sent Events client. It opens the stream, listens for the
 * configured events and invalidates their query keys, and — when the connection drops
 * (network blip, deploy, server restart) — closes the dead source and reopens it with
 * exponential backoff. `disconnect()` stops reconnecting and releases every listener.
 *
 * The stream endpoint does not exist yet (backend Phase 11); callers provide the URL and
 * subscriptions, so live wiring is a one-line change once it does.
 */
export class SseClient {
    private source: ServerEventSource | null = null;
    private retryHandle: ReturnType<typeof setTimeout> | null = null;
    private retryDelayMs: number;
    private stopped = true;
    private listeners: Array<{ event: string; listener: (event: MessageEvent) => void }> = [];

    constructor(private readonly options: SseClientOptions) {
        this.retryDelayMs = options.initialRetryDelayMs ?? DEFAULT_INITIAL_RETRY_DELAY_MS;
    }

    /** Open the stream. Idempotent while connected. */
    connect(): void {
        if (!this.stopped) return;
        this.stopped = false;
        this.retryDelayMs = this.options.initialRetryDelayMs ?? DEFAULT_INITIAL_RETRY_DELAY_MS;
        this.open();
    }

    /** Stop the stream and cancel any pending reconnect. Idempotent. */
    disconnect(): void {
        this.stopped = true;
        if (this.retryHandle !== null) {
            clearTimeout(this.retryHandle);
            this.retryHandle = null;
        }
        this.closeSource();
    }

    get isConnected(): boolean {
        return this.source !== null;
    }

    private open(): void {
        const factory = this.options.eventSourceFactory ?? defaultEventSourceFactory;
        const source = factory(this.options.url);
        this.source = source;

        source.onopen = () => {
            this.retryDelayMs = this.options.initialRetryDelayMs ?? DEFAULT_INITIAL_RETRY_DELAY_MS;
        };
        source.onerror = () => this.scheduleReconnect();

        for (const subscription of this.options.subscriptions) {
            const listener = () => this.invalidate(subscription.queryKeys);
            this.listeners.push({ event: subscription.event, listener });
            source.addEventListener(subscription.event, listener);
        }
    }

    private invalidate(queryKeys: readonly QueryKey[]): void {
        for (const queryKey of queryKeys) {
            void this.options.queryClient.invalidateQueries({ queryKey });
        }
    }

    private scheduleReconnect(): void {
        if (this.stopped) return;

        this.closeSource();

        const maxDelay = this.options.maxRetryDelayMs ?? DEFAULT_MAX_RETRY_DELAY_MS;
        this.retryHandle = setTimeout(() => {
            this.retryHandle = null;
            if (this.stopped) return;
            this.open();
        }, this.retryDelayMs);
        this.retryDelayMs = Math.min(this.retryDelayMs * 2, maxDelay);
    }

    private closeSource(): void {
        if (this.source === null) return;

        for (const { event, listener } of this.listeners) {
            this.source.removeEventListener(event, listener);
        }
        this.listeners = [];
        this.source.close();
        this.source = null;
    }
}
