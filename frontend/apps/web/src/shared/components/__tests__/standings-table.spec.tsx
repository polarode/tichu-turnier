import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import type { StandingsEntry } from '@/domain/standings.domain.ts';
import { StandingsTable } from '@/shared/components/standings-table.component.tsx';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';
import { bodyRows, columnHeaders } from '@/test-helpers/table-rows.ts';

/**
 * Server order: rank, then VP, then TP. Badgers and Falcons tie exactly and share rank 2.
 * Owls has the most TP but the lowest rank, so sorting by TP moves it to the top.
 */
const entries: StandingsEntry[] = [
    {
        rank: 1,
        teamId: 'ravens',
        teamName: 'Ravens',
        gamesPlayed: 2,
        victoryPoints: 8,
        tichuPoints: 250,
    },
    {
        rank: 2,
        teamId: 'badgers',
        teamName: 'Badgers',
        gamesPlayed: 2,
        victoryPoints: 4,
        tichuPoints: 200,
    },
    {
        rank: 2,
        teamId: 'falcons',
        teamName: 'Falcons',
        gamesPlayed: 2,
        victoryPoints: 4,
        tichuPoints: 200,
    },
    {
        rank: 4,
        teamId: 'owls',
        teamName: 'Owls',
        gamesPlayed: 2,
        victoryPoints: 0,
        tichuPoints: 300,
    },
];

const SERVER_ORDER = [
    ['Ravens', '1', '8', '250'],
    ['Badgers', '2', '4', '200'],
    ['Falcons', '2', '4', '200'],
    ['Owls', '4', '0', '300'],
];

const standingsTable = () => screen.getByRole('table', { name: 'Rangliste' });

describe('StandingsTable', () => {
    it('renders the rows in the given order under the Rank, Team, SP and TP columns', () => {
        renderWithProviders(<StandingsTable entries={entries} caption="Rangliste" />);

        expect(columnHeaders(standingsTable())).toEqual(['Platz', 'Team', 'SP', 'TP']);
        expect(bodyRows(standingsTable())).toEqual(SERVER_ORDER);
    });

    it('sorts by Tichu points descending, then ascending, then back to the server order', async () => {
        const user = userEvent.setup();
        renderWithProviders(<StandingsTable entries={entries} caption="Rangliste" />);
        const table = standingsTable();
        const tpHeader = within(table).getByRole('button', { name: 'TP' });

        await user.click(tpHeader);
        expect(bodyRows(table)).toEqual([
            ['Owls', '4', '0', '300'],
            ['Ravens', '1', '8', '250'],
            ['Badgers', '2', '4', '200'],
            ['Falcons', '2', '4', '200'],
        ]);

        await user.click(tpHeader);
        expect(bodyRows(table)).toEqual([
            ['Badgers', '2', '4', '200'],
            ['Falcons', '2', '4', '200'],
            ['Ravens', '1', '8', '250'],
            ['Owls', '4', '0', '300'],
        ]);

        await user.click(tpHeader);
        expect(bodyRows(table)).toEqual(SERVER_ORDER);
    });

    it('sorts by team name A to Z on the first click and keeps each rank verbatim', async () => {
        const user = userEvent.setup();
        renderWithProviders(<StandingsTable entries={entries} caption="Rangliste" />);
        const table = standingsTable();

        await user.click(within(table).getByRole('button', { name: 'Team' }));

        expect(bodyRows(table)).toEqual([
            ['Badgers', '2', '4', '200'],
            ['Falcons', '2', '4', '200'],
            ['Owls', '4', '0', '300'],
            ['Ravens', '1', '8', '250'],
        ]);
    });

    it('sorts by rank ascending first, which is the server order', async () => {
        const user = userEvent.setup();
        renderWithProviders(<StandingsTable entries={entries} caption="Rangliste" />);
        const table = standingsTable();

        await user.click(within(table).getByRole('button', { name: 'TP' }));
        await user.click(within(table).getByRole('button', { name: 'Platz' }));

        expect(within(table).getByRole('columnheader', { name: 'Platz' })).toHaveAttribute(
            'aria-sort',
            'ascending',
        );
        expect(bodyRows(table)).toEqual(SERVER_ORDER);
    });

    it('keeps the own-team row marked aria-current under a sort', async () => {
        const user = userEvent.setup();
        renderWithProviders(
            <StandingsTable entries={entries} caption="Rangliste" highlightTeamId="owls" />,
        );
        const table = standingsTable();

        expect(within(table).getByRole('row', { name: /Owls/ })).toHaveAttribute(
            'aria-current',
            'true',
        );

        await user.click(within(table).getByRole('button', { name: 'TP' }));

        expect(within(table).getByRole('row', { name: /Owls/ })).toHaveAttribute(
            'aria-current',
            'true',
        );
        expect(within(table).getByRole('row', { name: /Ravens/ })).not.toHaveAttribute(
            'aria-current',
        );
    });
});
