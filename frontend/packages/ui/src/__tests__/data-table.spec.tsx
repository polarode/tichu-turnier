import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { DataTable, type DataTableColumn, type DataTableProps } from '../data-table.tsx';

interface Team {
    id: string;
    name: string;
    points: number | null;
    note: string;
}

const TEAMS: readonly Team[] = [
    { id: 'r1', name: 'Zebras', points: 10, note: 'z' },
    { id: 'r2', name: 'Ärzte', points: 30, note: 'ae' },
    { id: 'r3', name: 'Bären', points: null, note: 'b' },
    { id: 'r4', name: 'Alpha', points: 30, note: 'a' },
    { id: 'r5', name: 'Mäuse', points: 20, note: 'm' },
];

const COLUMNS: readonly DataTableColumn<Team>[] = [
    {
        id: 'name',
        header: 'Team',
        rowHeader: true,
        cell: (team) => team.name,
        sortValue: (team) => team.name,
        sortFirst: 'asc',
    },
    {
        id: 'points',
        header: 'Points',
        align: 'end',
        cell: (team) => (team.points === null ? '–' : String(team.points)),
        sortValue: (team) => team.points,
    },
    { id: 'note', header: 'Note', cell: (team) => team.note },
];

function renderTable(
    props: Partial<Pick<DataTableProps<Team>, 'visibleColumns' | 'rowProps'>> = {},
) {
    return render(
        <DataTable
            columns={COLUMNS}
            rows={TEAMS}
            getRowId={(team) => team.id}
            caption="Teams"
            locale="de"
            {...props}
        />,
    );
}

function rowHeaderNames() {
    return screen.getAllByRole('rowheader').map((cell) => cell.textContent);
}

describe('DataTable', () => {
    it('renders the rows in the given order under a caption, with the row header column as row headers', () => {
        renderTable();

        expect(screen.getByRole('table', { name: 'Teams' })).toBeInTheDocument();
        expect(rowHeaderNames()).toEqual(['Zebras', 'Ärzte', 'Bären', 'Alpha', 'Mäuse']);
        const firstRow = screen.getByRole('row', { name: /Zebras/ });
        expect(within(firstRow).getByRole('cell', { name: 'z' })).toBeInTheDocument();
    });

    it('cycles a number column from descending to ascending to the given order', async () => {
        const user = userEvent.setup();
        renderTable();
        const points = () => screen.getByRole('columnheader', { name: 'Points' });

        await user.click(screen.getByRole('button', { name: 'Points' }));
        expect(points()).toHaveAttribute('aria-sort', 'descending');
        expect(rowHeaderNames()).toEqual(['Ärzte', 'Alpha', 'Mäuse', 'Zebras', 'Bären']);

        await user.click(screen.getByRole('button', { name: 'Points' }));
        expect(points()).toHaveAttribute('aria-sort', 'ascending');
        expect(rowHeaderNames()).toEqual(['Zebras', 'Mäuse', 'Ärzte', 'Alpha', 'Bären']);

        await user.click(screen.getByRole('button', { name: 'Points' }));
        expect(points()).not.toHaveAttribute('aria-sort');
        expect(rowHeaderNames()).toEqual(['Zebras', 'Ärzte', 'Bären', 'Alpha', 'Mäuse']);
    });

    it('sorts a text column A to Z first, comparing in the given locale', async () => {
        const user = userEvent.setup();
        renderTable();
        const team = () => screen.getByRole('columnheader', { name: 'Team' });

        await user.click(screen.getByRole('button', { name: 'Team' }));
        expect(team()).toHaveAttribute('aria-sort', 'ascending');
        expect(rowHeaderNames()).toEqual(['Alpha', 'Ärzte', 'Bären', 'Mäuse', 'Zebras']);

        await user.click(screen.getByRole('button', { name: 'Team' }));
        expect(team()).toHaveAttribute('aria-sort', 'descending');
        expect(rowHeaderNames()).toEqual(['Zebras', 'Mäuse', 'Bären', 'Ärzte', 'Alpha']);

        await user.click(screen.getByRole('button', { name: 'Team' }));
        expect(team()).not.toHaveAttribute('aria-sort');
        expect(rowHeaderNames()).toEqual(['Zebras', 'Ärzte', 'Bären', 'Alpha', 'Mäuse']);
    });

    it('sorts empty values last in both directions', async () => {
        const user = userEvent.setup();
        renderTable();

        await user.click(screen.getByRole('button', { name: 'Points' }));
        expect(rowHeaderNames()).toEqual(['Ärzte', 'Alpha', 'Mäuse', 'Zebras', 'Bären']);

        await user.click(screen.getByRole('button', { name: 'Points' }));
        expect(rowHeaderNames()).toEqual(['Zebras', 'Mäuse', 'Ärzte', 'Alpha', 'Bären']);
    });

    it('keeps the given order for equal values in both directions', async () => {
        const user = userEvent.setup();
        renderTable();

        await user.click(screen.getByRole('button', { name: 'Points' }));
        const descending = rowHeaderNames();
        expect(descending.indexOf('Ärzte')).toBeLessThan(descending.indexOf('Alpha'));

        await user.click(screen.getByRole('button', { name: 'Points' }));
        const ascending = rowHeaderNames();
        expect(ascending.indexOf('Ärzte')).toBeLessThan(ascending.indexOf('Alpha'));
    });

    it('replaces the previous sort when another column is sorted', async () => {
        const user = userEvent.setup();
        renderTable();

        await user.click(screen.getByRole('button', { name: 'Points' }));
        await user.click(screen.getByRole('button', { name: 'Team' }));

        expect(screen.getByRole('columnheader', { name: 'Team' })).toHaveAttribute(
            'aria-sort',
            'ascending',
        );
        expect(screen.getByRole('columnheader', { name: 'Points' })).not.toHaveAttribute(
            'aria-sort',
        );
        expect(
            screen.getAllByRole('columnheader').filter((th) => th.hasAttribute('aria-sort')),
        ).toHaveLength(1);
    });

    it('hides columns outside visibleColumns and passes rowProps to the body rows', () => {
        renderTable({
            visibleColumns: ['name', 'points'],
            rowProps: (team) =>
                team.id === 'r2' ? { 'aria-current': 'true', className: 'own-row' } : {},
        });

        expect(screen.queryByRole('columnheader', { name: 'Note' })).not.toBeInTheDocument();
        expect(screen.queryByRole('cell', { name: 'z' })).not.toBeInTheDocument();

        const own = screen.getByRole('row', { name: /Ärzte/ });
        expect(own).toHaveAttribute('aria-current', 'true');
        expect(own).toHaveClass('own-row');
        expect(screen.getByRole('row', { name: /Zebras/ })).not.toHaveAttribute('aria-current');
    });
});
