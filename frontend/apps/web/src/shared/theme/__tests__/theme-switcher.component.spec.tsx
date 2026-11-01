import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { THEME_STORAGE_KEY } from '@/shared/theme/theme.ts';
import { installMatchMedia, type FakeMatchMedia } from '@/test-helpers/match-media.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

let os: FakeMatchMedia;

beforeEach(() => {
    os = installMatchMedia({ dark: false });
});

afterEach(() => {
    os.uninstall();
    window.localStorage.clear();
    delete document.documentElement.dataset.theme;
});

describe('ThemeSwitcher', () => {
    it('switches between light, dark and system from the header and remembers the choice', async () => {
        const user = userEvent.setup();
        renderApp('/');

        const trigger = await screen.findByRole('button', { name: 'Farbschema' });
        expect(trigger).toHaveAccessibleDescription('System');
        expect(document.documentElement.dataset.theme).toBe('light');

        await user.click(trigger);
        expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveAttribute('aria-checked', 'true');
        await user.click(screen.getByRole('menuitemradio', { name: 'Dunkel' }));

        expect(document.documentElement.dataset.theme).toBe('dark');
        expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
        expect(trigger).toHaveAccessibleDescription('Dunkel');
        // The menu closes after a choice.
        expect(screen.queryByRole('menu', { name: 'Farbschema' })).not.toBeInTheDocument();

        await user.click(trigger);
        await user.click(screen.getByRole('menuitemradio', { name: 'Hell' }));
        expect(document.documentElement.dataset.theme).toBe('light');
        expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    });

    it('is fully operable from the keyboard (WAI-ARIA menu pattern)', async () => {
        const user = userEvent.setup();
        renderApp('/');

        const trigger = await screen.findByRole('button', { name: 'Farbschema' });
        trigger.focus();
        await user.keyboard('{Enter}');

        // Opening moves focus onto the checked item.
        expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveFocus();

        await user.keyboard('{ArrowDown}');
        expect(screen.getByRole('menuitemradio', { name: 'Hell' })).toHaveFocus();
        await user.keyboard('{ArrowUp}{ArrowUp}');
        expect(screen.getByRole('menuitemradio', { name: 'Dunkel' })).toHaveFocus();
        await user.keyboard('{Home}');
        expect(screen.getByRole('menuitemradio', { name: 'Hell' })).toHaveFocus();
        await user.keyboard('{End}');
        expect(screen.getByRole('menuitemradio', { name: 'System' })).toHaveFocus();

        // Escape closes without choosing and hands focus back to the trigger.
        await user.keyboard('{Escape}');
        expect(screen.queryByRole('menu', { name: 'Farbschema' })).not.toBeInTheDocument();
        expect(trigger).toHaveFocus();

        await user.keyboard('{Enter}{ArrowUp}{Enter}');
        expect(document.documentElement.dataset.theme).toBe('dark');
        expect(trigger).toHaveFocus();
    });

    it('applies a stored preference on load', async () => {
        window.localStorage.setItem(THEME_STORAGE_KEY, 'dark');
        renderApp('/');

        expect(await screen.findByRole('button', { name: 'Farbschema' })).toHaveAccessibleDescription('Dunkel');
        expect(document.documentElement.dataset.theme).toBe('dark');
    });
});
