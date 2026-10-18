/**
 * Languages the app ships with. German is the default — the product is
 * German-first, English is a first-class alternative.
 */
export const LOCALES = ['de', 'en'] as const;

export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = 'de';

/** localStorage key for the persisted language preference. */
export const LOCALE_STORAGE_KEY = 'tichu.locale';

export function isLocale(value: unknown): value is Locale {
    return typeof value === 'string' && (LOCALES as readonly string[]).includes(value);
}

/** Read the persisted locale, tolerating private-mode / unavailable storage. */
export function readStoredLocale(): Locale | null {
    if (typeof window === 'undefined') return null;
    try {
        const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
        return isLocale(stored) ? stored : null;
    } catch {
        return null;
    }
}

export function storeLocale(locale: Locale): void {
    try {
        window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    } catch {
        // Persisting the preference is best-effort; never break the app over it.
    }
}
