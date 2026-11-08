import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import {
    DropdownMenu,
    DropdownMenuCheckboxItem,
    DropdownMenuContent,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '../dropdown-menu.tsx';

function Harness({ onChange }: { onChange?: (checked: boolean) => void }) {
    const [pinned, setPinned] = useState(false);
    const [hidden, setHidden] = useState(true);
    return (
        <>
            <DropdownMenu>
                <DropdownMenuTrigger>Columns</DropdownMenuTrigger>
                <DropdownMenuContent>
                    <DropdownMenuLabel>Show</DropdownMenuLabel>
                    <DropdownMenuSeparator />
                    <DropdownMenuCheckboxItem
                        checked={pinned}
                        onCheckedChange={(checked) => {
                            setPinned(checked);
                            onChange?.(checked);
                        }}
                    >
                        Pinned
                    </DropdownMenuCheckboxItem>
                    <DropdownMenuCheckboxItem checked={hidden} onCheckedChange={setHidden}>
                        Hidden
                    </DropdownMenuCheckboxItem>
                </DropdownMenuContent>
            </DropdownMenu>
            <output>{`pinned ${pinned}`}</output>
        </>
    );
}

describe('DropdownMenu', () => {
    it('shows its checkbox items with their labels when the trigger is opened', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        expect(screen.queryByRole('menu')).not.toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Columns' }));

        expect(screen.getByRole('menu')).toBeInTheDocument();
        expect(screen.getByRole('menuitemcheckbox', { name: 'Pinned' })).toBeInTheDocument();
        expect(screen.getByRole('menuitemcheckbox', { name: 'Hidden' })).toBeInTheDocument();
    });

    it('exposes the checked state of each item', async () => {
        const user = userEvent.setup();
        render(<Harness />);

        await user.click(screen.getByRole('button', { name: 'Columns' }));

        expect(screen.getByRole('menuitemcheckbox', { name: 'Pinned' })).toHaveAttribute(
            'aria-checked',
            'false',
        );
        expect(screen.getByRole('menuitemcheckbox', { name: 'Hidden' })).toHaveAttribute(
            'aria-checked',
            'true',
        );
    });

    it('calls onCheckedChange with true when an unchecked item is clicked', async () => {
        const user = userEvent.setup();
        const onChange = vi.fn();
        render(<Harness onChange={onChange} />);

        await user.click(screen.getByRole('button', { name: 'Columns' }));
        await user.click(screen.getByRole('menuitemcheckbox', { name: 'Pinned' }));

        expect(onChange).toHaveBeenCalledWith(true);
        expect(screen.getByText('pinned true')).toBeInTheDocument();
    });
});
