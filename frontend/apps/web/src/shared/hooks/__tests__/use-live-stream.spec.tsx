import type { ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { focusManager, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useLiveStream } from '@/shared/hooks/use-live-stream.hook.ts';
import { FakeEventSource } from '@/test-helpers/fake-event-source.ts';

const keysOnConnect = [['table']];

function renderLiveStream() {
    const queryClient = new QueryClient();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const view = renderHook(
        () =>
            useLiveStream({
                url: '/api/team/events',
                enabled: true,
                subscriptions: [],
                keysOnConnect,
            }),
        { wrapper },
    );
    return { ...view, invalidate };
}

function latestSource(): FakeEventSource {
    return FakeEventSource.instances.at(-1)!;
}

beforeEach(() => {
    focusManager.setFocused(true);
});

afterEach(() => {
    // Hand focus back to the document's visibility for the next spec.
    focusManager.setFocused(undefined);
    vi.useRealTimers();
});

describe('useLiveStream while the page is hidden (phone asleep, app in the background)', () => {
    it('closes the stream when the page is hidden and reports offline', () => {
        const { result } = renderLiveStream();
        const source = latestSource();
        act(() => source.emitOpen());
        expect(result.current).toBe(true);

        act(() => focusManager.setFocused(false));

        expect(source.closed).toBe(true);
        expect(result.current).toBe(false);
    });

    it('reopens the stream when the page is visible again and catches up once it opens', () => {
        const { result, invalidate } = renderLiveStream();
        act(() => latestSource().emitOpen());
        act(() => focusManager.setFocused(false));
        invalidate.mockClear();

        act(() => focusManager.setFocused(true));

        expect(FakeEventSource.instances).toHaveLength(2);
        const reopened = latestSource();
        expect(reopened.closed).toBe(false);
        act(() => reopened.emitOpen());
        expect(result.current).toBe(true);
        expect(invalidate).toHaveBeenCalledWith({ queryKey: ['table'] });
    });

    it('reconnects right away on return, without waiting out the backoff', () => {
        vi.useFakeTimers();
        renderLiveStream();
        // Two failed attempts push the next retry out to 4 s.
        act(() => latestSource().emitError());
        act(() => vi.advanceTimersByTime(1_000));
        act(() => latestSource().emitError());
        const attempts = FakeEventSource.instances.length;
        act(() => focusManager.setFocused(false));

        act(() => focusManager.setFocused(true));

        expect(FakeEventSource.instances).toHaveLength(attempts + 1);
        expect(latestSource().closed).toBe(false);
    });

    it('waits for the page to be visible before opening the stream', () => {
        focusManager.setFocused(false);
        renderLiveStream();
        expect(FakeEventSource.instances).toHaveLength(0);

        act(() => focusManager.setFocused(true));

        expect(FakeEventSource.instances).toHaveLength(1);
    });
});
