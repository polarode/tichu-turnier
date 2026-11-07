import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { Switch } from '../switch.tsx';

describe('Switch', () => {
    it('renders a switch named by its aria-label', () => {
        render(<Switch aria-label="Public" />);
        expect(screen.getByRole('switch', { name: 'Public' })).toBeInTheDocument();
    });

    it('reflects the checked prop', () => {
        const { rerender } = render(<Switch aria-label="Public" checked={false} />);
        expect(screen.getByRole('switch', { name: 'Public' })).toHaveAttribute(
            'aria-checked',
            'false',
        );

        rerender(<Switch aria-label="Public" checked />);
        expect(screen.getByRole('switch', { name: 'Public' })).toHaveAttribute(
            'aria-checked',
            'true',
        );
    });

    it('calls onCheckedChange with the next value when the user toggles it', async () => {
        const user = userEvent.setup();
        const onCheckedChange = vi.fn();
        render(<Switch aria-label="Public" checked={false} onCheckedChange={onCheckedChange} />);

        await user.click(screen.getByRole('switch', { name: 'Public' }));
        expect(onCheckedChange).toHaveBeenCalledWith(true);
    });

    it('does not call onCheckedChange when disabled', async () => {
        const user = userEvent.setup();
        const onCheckedChange = vi.fn();
        render(<Switch aria-label="Public" disabled onCheckedChange={onCheckedChange} />);

        await user.click(screen.getByRole('switch', { name: 'Public' }));
        expect(onCheckedChange).not.toHaveBeenCalled();
    });
});
