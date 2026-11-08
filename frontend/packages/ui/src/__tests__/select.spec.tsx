import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../select.tsx';

function Harness({ invalid = false }: { invalid?: boolean }) {
    const [value, setValue] = useState('200');
    return (
        <>
            <label htmlFor="bonus">Bonus</label>
            <Select value={value} onValueChange={setValue}>
                <SelectTrigger id="bonus" aria-invalid={invalid ? true : undefined}>
                    <SelectValue />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="100">100</SelectItem>
                    <SelectItem value="200">200</SelectItem>
                </SelectContent>
            </Select>
            <output>{`chosen ${value}`}</output>
        </>
    );
}

describe('Select', () => {
    it('is a combobox named by its label, showing the current value while closed', () => {
        render(<Harness />);
        const trigger = screen.getByRole('combobox', { name: 'Bonus' });
        expect(trigger).toHaveTextContent('200');
        expect(trigger).toHaveAttribute('aria-expanded', 'false');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    });

    it('opens a listbox on click and selects the clicked option', async () => {
        const user = userEvent.setup();
        render(<Harness />);

        await user.click(screen.getByRole('combobox', { name: 'Bonus' }));
        expect(screen.getByRole('listbox')).toBeInTheDocument();
        expect(screen.getByRole('option', { name: '200' })).toHaveAttribute(
            'aria-selected',
            'true',
        );

        await user.click(screen.getByRole('option', { name: '100' }));
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Bonus' })).toHaveTextContent('100');
        expect(screen.getByText('chosen 100')).toBeInTheDocument();
    });

    it('is operable by keyboard: open, move with arrows, pick with Enter, focus returns', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        const trigger = screen.getByRole('combobox', { name: 'Bonus' });
        trigger.focus();

        await user.keyboard('{Enter}');
        expect(screen.getByRole('listbox')).toBeInTheDocument();
        expect(screen.getByRole('option', { name: '200' })).toHaveFocus();

        await user.keyboard('{ArrowUp}');
        expect(screen.getByRole('option', { name: '100' })).toHaveFocus();

        await user.keyboard('{Enter}');
        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        expect(screen.getByText('chosen 100')).toBeInTheDocument();
        expect(trigger).toHaveFocus();
    });

    it('closes on Escape without changing the value', async () => {
        const user = userEvent.setup();
        render(<Harness />);
        screen.getByRole('combobox', { name: 'Bonus' }).focus();

        await user.keyboard('{Enter}');
        await user.keyboard('{ArrowUp}');
        await user.keyboard('{Escape}');

        expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        expect(screen.getByText('chosen 200')).toBeInTheDocument();
    });

    it('forwards aria-invalid to the trigger', () => {
        render(<Harness invalid />);
        expect(screen.getByRole('combobox', { name: 'Bonus' })).toHaveAttribute(
            'aria-invalid',
            'true',
        );
    });
});
