import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { Popover, PopoverContent, PopoverTrigger } from '../popover.tsx';

function Harness() {
    return (
        <Popover>
            <PopoverTrigger>Details</PopoverTrigger>
            <PopoverContent>Round 3 pairing notes</PopoverContent>
        </Popover>
    );
}

describe('Popover', () => {
    it('shows its content when the trigger is clicked', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Details' }));

        expect(screen.getByRole('dialog')).toBeInTheDocument();
        expect(screen.getByText('Round 3 pairing notes')).toBeInTheDocument();
    });

    it('closes when Escape is pressed', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        await user.click(screen.getByRole('button', { name: 'Details' }));

        await user.keyboard('{Escape}');

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
});
