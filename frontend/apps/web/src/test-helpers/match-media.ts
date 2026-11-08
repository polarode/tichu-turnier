import { DARK_SCHEME_QUERY } from '@/shared/theme/theme.ts';

export interface FakeMatchMedia {
    /** Flip the OS dark-mode setting and notify every listener. */
    setDark: (dark: boolean) => void;
    listenerCount: () => number;
    /** Restore the original `window.matchMedia`. */
    uninstall: () => void;
}

/**
 * Replace `window.matchMedia` with a controllable fake for the
 * `prefers-color-scheme: dark` query, so tests can change the OS theme live.
 */
export function installMatchMedia({ dark }: { dark: boolean }): FakeMatchMedia {
    const original = window.matchMedia;
    let matches = dark;
    const listeners = new Set<(event: MediaQueryListEvent) => void>();

    window.matchMedia = (query: string) =>
        ({
            get matches() {
                return query === DARK_SCHEME_QUERY && matches;
            },
            media: query,
            onchange: null,
            addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
                listeners.add(listener);
            },
            removeEventListener: (
                _type: string,
                listener: (event: MediaQueryListEvent) => void,
            ) => {
                listeners.delete(listener);
            },
            addListener: () => undefined,
            removeListener: () => undefined,
            dispatchEvent: () => true,
        }) as unknown as MediaQueryList;

    return {
        setDark: (next) => {
            matches = next;
            for (const listener of listeners) {
                listener({ matches: next, media: DARK_SCHEME_QUERY } as MediaQueryListEvent);
            }
        },
        listenerCount: () => listeners.size,
        uninstall: () => {
            window.matchMedia = original;
        },
    };
}
