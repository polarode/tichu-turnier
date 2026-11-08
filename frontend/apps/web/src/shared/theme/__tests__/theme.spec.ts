import { afterEach, describe, expect, it, vi } from 'vitest';

import {
    readStoredTheme,
    resolveTheme,
    storeTheme,
    THEME_STORAGE_KEY,
} from '@/shared/theme/theme.ts';

afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
});

describe('resolveTheme', () => {
    it('follows the OS only for the system preference', () => {
        expect(resolveTheme('system', true)).toBe('dark');
        expect(resolveTheme('system', false)).toBe('light');
        expect(resolveTheme('light', true)).toBe('light');
        expect(resolveTheme('dark', false)).toBe('dark');
    });
});

describe('stored theme preference', () => {
    it('round-trips a valid preference', () => {
        storeTheme('dark');
        expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
        expect(readStoredTheme()).toBe('dark');
    });

    it('ignores an unknown stored value', () => {
        window.localStorage.setItem(THEME_STORAGE_KEY, 'sepia');
        expect(readStoredTheme()).toBeNull();
    });

    it('tolerates unavailable storage on read and write', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('denied');
        });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('denied');
        });
        expect(readStoredTheme()).toBeNull();
        expect(() => storeTheme('light')).not.toThrow();
    });
});
