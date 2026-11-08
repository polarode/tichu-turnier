import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { LiveBadge } from '@/shared/components/live-badge.component.tsx';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';

const HINT = 'While live, new results appear without reloading the page.';

describe('LiveBadge', () => {
    it('shows the Live label, the hint and a pulsing halo while connected', () => {
        const { container } = renderWithProviders(
            <LiveBadge connected labelKeyPrefix="tournament.live" />,
            { initialLocale: 'en' },
        );

        expect(screen.getByText('Live')).toBeInTheDocument();
        expect(screen.getByTitle(HINT)).toBeInTheDocument();
        expect(container.querySelector('.animate-ping')).not.toBeNull();
    });

    it('shows the Offline label without a pulsing halo while disconnected', () => {
        const { container } = renderWithProviders(
            <LiveBadge connected={false} labelKeyPrefix="tournament.live" />,
            { initialLocale: 'en' },
        );

        expect(screen.getByText('Offline')).toBeInTheDocument();
        expect(screen.getByTitle(HINT)).toBeInTheDocument();
        expect(container.querySelector('.animate-ping')).toBeNull();
    });
});
