/**
 * Best-effort localStorage access for small user preferences (language, theme).
 * Reads validate the stored value and both directions swallow storage errors,
 * so private mode or blocked site data never breaks the app.
 */
export function readStoredPreference<T extends string>(
    key: string,
    isValid: (value: unknown) => value is T,
): T | null {
    if (typeof window === 'undefined') return null;
    try {
        const stored = window.localStorage.getItem(key);
        return isValid(stored) ? stored : null;
    } catch {
        return null;
    }
}

export function storePreference(key: string, value: string): void {
    try {
        window.localStorage.setItem(key, value);
    } catch {
        // Persisting the preference is best-effort; never break the app over it.
    }
}
