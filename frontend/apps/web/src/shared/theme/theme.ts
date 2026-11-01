import { readStoredPreference, storePreference } from '@/shared/storage/stored-preference.ts';

/**
 * Colour themes. The user picks a preference; `system` follows the OS setting
 * and is the default, so a phone in dark mode gets the dark theme unasked.
 */
export const THEME_PREFERENCES = ['light', 'dark', 'system'] as const;

export type ThemePreference = (typeof THEME_PREFERENCES)[number];

/** The theme actually applied to the page, written to `<html data-theme>`. */
export type ResolvedTheme = 'light' | 'dark';

export const DEFAULT_THEME_PREFERENCE: ThemePreference = 'system';

/**
 * localStorage key for the persisted theme preference. `index.html` reads the
 * same key in an inline script to set the theme before first paint.
 */
export const THEME_STORAGE_KEY = 'tichu.theme';

/** Media query for the OS dark-mode setting. */
export const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)';

export function isThemePreference(value: unknown): value is ThemePreference {
    return typeof value === 'string' && (THEME_PREFERENCES as readonly string[]).includes(value);
}

export function resolveTheme(preference: ThemePreference, osPrefersDark: boolean): ResolvedTheme {
    if (preference === 'system') return osPrefersDark ? 'dark' : 'light';
    return preference;
}

/** Read the persisted preference, tolerating private-mode / unavailable storage. */
export function readStoredTheme(): ThemePreference | null {
    return readStoredPreference(THEME_STORAGE_KEY, isThemePreference);
}

export function storeTheme(preference: ThemePreference): void {
    storePreference(THEME_STORAGE_KEY, preference);
}
