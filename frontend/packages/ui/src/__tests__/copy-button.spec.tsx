import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CopyButton } from '../copy-button.tsx';

describe('CopyButton', () => {
    beforeEach(() => {
        Object.defineProperty(navigator, 'clipboard', {
            value: { writeText: vi.fn().mockResolvedValue(undefined) },
            configurable: true,
        });
    });

    it('shows the copy label, writes to the clipboard and confirms', async () => {
        render(<CopyButton value="secret" copyLabel="Copy" copiedLabel="Copied" />);
        await userEvent.click(screen.getByRole('button', { name: 'Copy' }));
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith('secret');
        expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument();
    });

    it('still confirms when the clipboard API is absent', async () => {
        Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
        render(<CopyButton value="secret" copyLabel="Copy" copiedLabel="Copied" />);
        await userEvent.click(screen.getByRole('button', { name: 'Copy' }));
        expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument();
    });
});
