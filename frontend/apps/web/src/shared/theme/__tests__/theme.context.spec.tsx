import { act, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ThemeProvider } from '@/shared/theme/theme.context.tsx';
import { THEME_STORAGE_KEY } from '@/shared/theme/theme.ts';
import { installMatchMedia, type FakeMatchMedia } from '@/test-helpers/match-media.ts';

let os: FakeMatchMedia;

beforeEach(() => {
    os = installMatchMedia({ dark: false });
});

afterEach(() => {
    os.uninstall();
    window.localStorage.clear();
    delete document.documentElement.dataset.theme;
});

const theme = () => document.documentElement.dataset.theme;

describe('ThemeProvider', () => {
    it('defaults to the system preference and follows the OS as it changes', () => {
        render(<ThemeProvider>app</ThemeProvider>);
        expect(theme()).toBe('light');

        act(() => os.setDark(true));
        expect(theme()).toBe('dark');

        act(() => os.setDark(false));
        expect(theme()).toBe('light');
    });

    it('keeps an explicit choice regardless of the OS setting', () => {
        window.localStorage.setItem(THEME_STORAGE_KEY, 'light');
        render(<ThemeProvider>app</ThemeProvider>);
        expect(theme()).toBe('light');

        act(() => os.setDark(true));
        expect(theme()).toBe('light');
    });

    it('stops listening to the OS once unmounted', () => {
        const { unmount } = render(<ThemeProvider>app</ThemeProvider>);
        unmount();
        expect(os.listenerCount()).toBe(0);
    });
});
