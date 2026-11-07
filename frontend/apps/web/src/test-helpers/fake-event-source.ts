import type { ServerEventSource } from '@/shared/sse/sse-client.ts';

/** Minimal `EventSource` double: records listeners and lets tests drive open/error/events. */
export class FakeEventSource implements ServerEventSource {
    static instances: FakeEventSource[] = [];

    static reset(): void {
        FakeEventSource.instances = [];
    }

    onopen: ((event: Event) => void) | null = null;
    onerror: ((event: Event) => void) | null = null;
    closed = false;
    readonly listeners = new Map<string, Set<(event: MessageEvent) => void>>();

    constructor(
        readonly url: string,
        readonly init?: EventSourceInit,
    ) {
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
