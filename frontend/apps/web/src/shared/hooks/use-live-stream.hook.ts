import { useEffect, useRef, useState } from 'react';
import { focusManager, onlineManager, type QueryKey, useQueryClient } from '@tanstack/react-query';

import { SseClient, type SseSubscription } from '@/shared/sse/sse-client.ts';

export interface LiveStreamOptions {
    /** Stream path, resolved against the API base by the SSE client. */
    url: string;
    /** Whether the stream should be open at all. */
    enabled: boolean;
    /** Which stream events invalidate which query keys. */
    subscriptions: SseSubscription[];
    /** Query keys invalidated on every (re)connect, to catch up on events missed while offline. */
    keysOnConnect: QueryKey[];
}

/**
 * Follows a live event stream while `enabled`: each event refetches the queries it makes
 * stale (see `subscriptions`), and every (re)connect refetches `keysOnConnect`, so events
 * missed while offline are caught up. Returns whether the stream is connected.
 *
 * The stream only stays open while the page is visible and the device online, by TanStack's
 * `focusManager` and `onlineManager`: a phone asleep, an app in the background or a lost
 * network closes it, and coming back reopens it at once, without waiting out the reconnect
 * backoff. A connection the device dropped silently in its sleep, still looking open, is
 * replaced the same way.
 *
 * The stream is opened per `url` and `enabled` only. `subscriptions` and `keysOnConnect`
 * are read at connect time, so callers may pass fresh arrays on every render without
 * reconnecting.
 */
export function useLiveStream({
    url,
    enabled,
    subscriptions,
    keysOnConnect,
}: LiveStreamOptions): boolean {
    const queryClient = useQueryClient();
    const [connected, setConnected] = useState(false);
    const latest = useRef({ subscriptions, keysOnConnect });

    useEffect(() => {
        latest.current = { subscriptions, keysOnConnect };
    });

    useEffect(() => {
        if (!enabled) return;
        const client = new SseClient({
            url,
            queryClient,
            subscriptions: latest.current.subscriptions,
            onStateChange: (isConnected) => {
                setConnected(isConnected);
                if (!isConnected) return;
                for (const queryKey of latest.current.keysOnConnect) {
                    void queryClient.invalidateQueries({ queryKey });
                }
            },
        });
        const followDevice = () => {
            if (focusManager.isFocused() && onlineManager.isOnline()) client.connect();
            else client.disconnect();
        };
        followDevice();
        const unsubscribeFocus = focusManager.subscribe(followDevice);
        const unsubscribeOnline = onlineManager.subscribe(followDevice);
        return () => {
            unsubscribeFocus();
            unsubscribeOnline();
            client.disconnect();
            setConnected(false);
        };
    }, [url, enabled, queryClient]);

    return connected;
}
