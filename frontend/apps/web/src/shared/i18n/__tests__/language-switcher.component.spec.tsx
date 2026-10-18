import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { LanguageSwitcher } from '@/shared/i18n/language-switcher.component.tsx';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';

describe('LanguageSwitcher', () => {
    it('marks the active language and switches when the other is clicked', async () => {
        renderWithProviders(<LanguageSwitcher />, { initialLocale: 'de' });

        expect(screen.getByRole('button', { name: 'Deutsch' })).toHaveAttribute(
            'aria-pressed',
            'true',
        );
        expect(screen.getByRole('button', { name: 'Englisch' })).toHaveAttribute(
            'aria-pressed',
            'false',
        );

        await userEvent.click(screen.getByRole('button', { name: 'Englisch' }));

        expect(screen.getByRole('button', { name: 'English' })).toHaveAttribute(
            'aria-pressed',
            'true',
        );
        expect(screen.getByRole('button', { name: 'German' })).toHaveAttribute(
            'aria-pressed',
            'false',
        );
        expect(document.documentElement.lang).toBe('en');
    });
});
