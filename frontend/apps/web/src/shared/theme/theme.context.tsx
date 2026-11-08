import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useState,
    useSyncExternalStore,
} from 'react';
import type { ReactNode } from 'react';

import {
    DARK_SCHEME_QUERY,
    DEFAULT_THEME_PREFERENCE,
    readStoredTheme,
    resolveTheme,
    storeTheme,
    type ThemePreference,
} from '@/shared/theme/theme.ts';

export interface Theme {
    preference: ThemePreference;
    setPreference: (preference: ThemePreference) => void;
}

const ThemeContext = createContext<Theme | null>(null);

function osPrefersDark(): boolean {
    return typeof window.matchMedia === 'function' && window.matchMedia(DARK_SCHEME_QUERY).matches;
}

function subscribeToOsScheme(onChange: () => void): () => void {
    if (typeof window.matchMedia !== 'function') return () => undefined;
    const query = window.matchMedia(DARK_SCHEME_QUERY);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
}

/**
 * Holds the theme preference and writes the resolved theme to `<html data-theme>`,
 * which the `@tichu/ui` tokens key their dark values on. The OS setting is read
 * live, so a `system` preference follows it as it changes.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
    const [preference, setPreferenceState] = useState<ThemePreference>(
        () => readStoredTheme() ?? DEFAULT_THEME_PREFERENCE,
    );
    // The OS setting is an external store: read it live rather than mirroring it in state.
    const prefersDark = useSyncExternalStore(subscribeToOsScheme, osPrefersDark);

    const resolved = resolveTheme(preference, prefersDark);

    useEffect(() => {
        document.documentElement.dataset.theme = resolved;
    }, [resolved]);

    const setPreference = useCallback((next: ThemePreference) => {
        setPreferenceState(next);
        storeTheme(next);
    }, []);

    const value = useMemo<Theme>(
        () => ({ preference, setPreference }),
        [preference, setPreference],
    );

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): Theme {
    const value = useContext(ThemeContext);
    if (value === null) {
        throw new Error('useTheme must be used inside a ThemeProvider');
    }
    return value;
}
