import { DESKTOP_MEDIA_QUERY } from '@/shared/constants/statistics-columns.ts';
import { DARK_SCHEME_QUERY } from '@/shared/theme/theme.ts';

export interface FakeMatchMedia {
    /** Flip the OS dark-mode setting and notify every listener. */
    setDark: (dark: boolean) => void;
    /** Change the viewport width that `(min-width: …px)` queries answer for, and notify listeners. */
    setWidth: (width: number) => void;
    listenerCount: () => number;
    /** Restore the original `window.matchMedia`. */
    uninstall: () => void;
}

const MIN_WIDTH_QUERY = /^\(min-width: (\d+)px\)$/;

/**
 * Replace `window.matchMedia` with a controllable fake. It answers the
 * `prefers-color-scheme: dark` query from `dark` and `(min-width: Npx)` queries
 * from the viewport `width` (a desktop-sized 1024px unless told otherwise), so
 * tests can change the OS theme or resize the viewport live.
 */
export function installMatchMedia({
    dark,
    width = 1024,
}: {
    dark: boolean;
    width?: number;
}): FakeMatchMedia {
    const original = window.matchMedia;
    let matches = dark;
    let viewportWidth = width;
    const listeners = new Set<(event: MediaQueryListEvent) => void>();

    const answer = (query: string): boolean => {
        if (query === DARK_SCHEME_QUERY) return matches;
        const minWidth = MIN_WIDTH_QUERY.exec(query);
        return minWidth !== null && viewportWidth >= Number(minWidth[1]);
    };

    window.matchMedia = (query: string) =>
        ({
            get matches() {
                return answer(query);
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

    const notify = (query: string) => {
        for (const listener of listeners) {
            listener({ matches: answer(query), media: query } as MediaQueryListEvent);
        }
    };

    return {
        setDark: (next) => {
            matches = next;
            notify(DARK_SCHEME_QUERY);
        },
        setWidth: (next) => {
            viewportWidth = next;
            notify(DESKTOP_MEDIA_QUERY);
        },
        listenerCount: () => listeners.size,
        uninstall: () => {
            window.matchMedia = original;
        },
    };
}
