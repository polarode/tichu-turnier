import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '../dialog.tsx';

function Harness({ closeLabel }: { closeLabel?: string }) {
    const [open, setOpen] = useState(false);
    return (
        <>
            <button type="button" onClick={() => setOpen(true)}>
                Open
            </button>
            <Dialog open={open} onOpenChange={setOpen}>
                <DialogContent closeLabel={closeLabel}>
                    <DialogHeader>
                        <DialogTitle>Rotate link</DialogTitle>
                        <DialogDescription>The old link stops working.</DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <button type="button" onClick={() => setOpen(false)}>
                            Cancel
                        </button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}

describe('Dialog', () => {
    it('is a modal dialog named by its title and described by its description', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        await user.click(screen.getByRole('button', { name: 'Open' }));

        const dialog = screen.getByRole('dialog', { name: 'Rotate link' });
        expect(dialog).toHaveAccessibleDescription('The old link stops working.');
    });

    it('moves focus into the dialog on open and back to the opener on close', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        const opener = screen.getByRole('button', { name: 'Open' });
        await user.click(opener);

        expect(screen.getByRole('dialog')).toContainElement(document.activeElement as HTMLElement);

        await user.click(screen.getByRole('button', { name: 'Cancel' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(opener).toHaveFocus();
    });

    it('closes on Escape', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        await user.click(screen.getByRole('button', { name: 'Open' }));

        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('offers a corner close button only when given a localized label', async () => {
        const user = userEvent.setup();
        const { unmount } = render(<Harness />);
        await user.click(screen.getByRole('button', { name: 'Open' }));
        expect(screen.queryByRole('button', { name: 'Schließen' })).not.toBeInTheDocument();
        unmount();

        render(<Harness closeLabel="Schließen" />);
        await user.click(screen.getByRole('button', { name: 'Open' }));
        await user.click(screen.getByRole('button', { name: 'Schließen' }));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
});
