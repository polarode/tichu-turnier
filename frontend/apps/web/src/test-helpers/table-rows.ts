import { within } from '@testing-library/react';

/** A table's column header texts, read through their accessible role. */
export function columnHeaders(table: HTMLElement): string[] {
    return within(table)
        .getAllByRole('columnheader')
        .map((header) => header.textContent ?? '');
}

/** A table's body rows as text: the row header first, then each cell. */
export function bodyRows(table: HTMLElement): string[][] {
    return within(table)
        .getAllByRole('row')
        .filter((row) => within(row).queryByRole('rowheader') !== null)
        .map((row) => [
            within(row).getByRole('rowheader').textContent ?? '',
            ...within(row)
                .getAllByRole('cell')
                .map((cell) => cell.textContent ?? ''),
        ]);
}
