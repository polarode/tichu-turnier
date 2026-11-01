import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { LanguageSwitcher } from '@/shared/i18n/language-switcher.component.tsx';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';

describe('LanguageSwitcher', () => {
    it('shows the active language as a flag button whose menu switches languages', async () => {
        renderWithProviders(<LanguageSwitcher />, { initialLocale: 'de' });

        const trigger = screen.getByRole('button', { name: /Sprache/ });
        expect(trigger).toHaveAccessibleDescription('Deutsch');

        // Menu is closed until the trigger is clicked.
        expect(screen.queryByRole('menuitemradio', { name: 'Deutsch' })).not.toBeInTheDocument();
        await userEvent.click(trigger);

        await userEvent.click(screen.getByRole('menuitemradio', { name: 'Englisch' }));

        expect(document.documentElement.lang).toBe('en');
        // The trigger now reflects the new active language and the menu closed.
        expect(screen.getByRole('button', { name: /Language/ })).toHaveAccessibleDescription(
            'English',
        );
        expect(screen.queryByRole('menuitem', { name: 'Englisch' })).not.toBeInTheDocument();
    });
});
