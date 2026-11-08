import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { TabList, TabPanel } from '../tabs.tsx';

const ITEMS = [
    { value: 'a', label: 'Alpha' },
    { value: 'b', label: 'Beta', done: true },
    { value: 'c', label: 'Gamma' },
] as const;

function Harness({ variant }: { variant?: 'segmented' | 'underline' }) {
    const [value, setValue] = useState<'a' | 'b' | 'c'>('a');
    return (
        <>
            <TabList
                id="t"
                label="Sections"
                variant={variant}
                items={ITEMS}
                value={value}
                onValueChange={setValue}
            />
            <TabPanel id="t" value={value}>{`content ${value}`}</TabPanel>
        </>
    );
}

describe('Tabs', () => {
    it('exposes a named tablist whose panel is labelled by the selected tab', () => {
        render(<Harness />);
        expect(screen.getByRole('tablist', { name: 'Sections' })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('tabpanel', { name: 'Alpha' })).toHaveTextContent('content a');
    });

    it('keeps only the selected tab in the Tab order', () => {
        render(<Harness />);
        expect(screen.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('tabindex', '0');
        expect(screen.getByRole('tab', { name: 'Beta' })).toHaveAttribute('tabindex', '-1');
    });

    it('selects on click', async () => {
        render(<Harness />);
        await userEvent.click(screen.getByRole('tab', { name: 'Gamma' }));
        expect(screen.getByRole('tabpanel', { name: 'Gamma' })).toHaveTextContent('content c');
    });

    it('moves selection and focus with arrows, wrapping, and with Home/End', async () => {
        const user = userEvent.setup();
        render(<Harness variant="underline" />);
        screen.getByRole('tab', { name: 'Alpha' }).focus();

        await user.keyboard('{ArrowLeft}');
        expect(screen.getByRole('tab', { name: 'Gamma' })).toHaveFocus();
        expect(screen.getByRole('tab', { name: 'Gamma' })).toHaveAttribute('aria-selected', 'true');

        await user.keyboard('{ArrowRight}');
        expect(screen.getByRole('tab', { name: 'Alpha' })).toHaveFocus();

        await user.keyboard('{End}');
        expect(screen.getByRole('tab', { name: 'Gamma' })).toHaveFocus();

        await user.keyboard('{Home}');
        expect(screen.getByRole('tab', { name: 'Alpha' })).toHaveAttribute('aria-selected', 'true');
    });

    it('fills done segmented tabs with the secondary variant', () => {
        render(<Harness />);
        expect(screen.getByRole('tab', { name: 'Beta' })).toHaveClass('bg-secondary');
        expect(screen.getByRole('tab', { name: 'Gamma' })).toHaveClass('border-input');
    });
});
