import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import {
    Table,
    TableBody,
    TableCaption,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '../table.tsx';

describe('Table', () => {
    it('exposes a table named by its caption with column headers, row headers and cells', () => {
        render(
            <Table>
                <TableCaption>Standings</TableCaption>
                <TableHeader>
                    <TableRow>
                        <TableHead>Team</TableHead>
                        <TableHead>Points</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    <TableRow>
                        <TableHead scope="row">Alpha</TableHead>
                        <TableCell>12</TableCell>
                    </TableRow>
                </TableBody>
            </Table>,
        );

        expect(screen.getByRole('table', { name: 'Standings' })).toBeInTheDocument();
        expect(screen.getByRole('columnheader', { name: 'Points' })).toBeInTheDocument();
        expect(screen.getByRole('rowheader', { name: 'Alpha' })).toBeInTheDocument();
        expect(screen.getByRole('cell', { name: '12' })).toBeInTheDocument();
    });

    it('passes className and extra props through to the rendered element', () => {
        render(
            <Table className="custom-table" aria-label="Players">
                <TableBody>
                    <TableRow className="custom-row" data-testid="row">
                        <TableCell className="custom-cell">7</TableCell>
                    </TableRow>
                </TableBody>
            </Table>,
        );

        expect(screen.getByRole('table', { name: 'Players' })).toHaveClass('custom-table');
        expect(screen.getByTestId('row')).toHaveClass('custom-row');
        expect(screen.getByRole('cell', { name: '7' })).toHaveClass('custom-cell');
    });
});
