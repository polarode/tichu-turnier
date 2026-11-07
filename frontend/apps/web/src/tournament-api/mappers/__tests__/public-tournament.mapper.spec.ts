import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import {
    publicTournamentDetailDtoSchema,
    type PublicTournamentDetailDto,
    type PublicTournamentDto,
} from '@/tournament-api/dtos/public-tournament.dto.ts';
import {
    publicTournamentDetailDtoToDomain,
    publicTournamentDtoToDomain,
} from '@/tournament-api/mappers/public-tournament.mapper.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/** Reads a fixed position of a fixture list, failing loudly if the fixture is shorter. */
function at<T>(items: T[], index: number): T {
    const item = items[index];
    if (item === undefined) {
        throw new Error(`no element at index ${index}`);
    }
    return item;
}

const dto: PublicTournamentDetailDto = {
    id: 't-2',
    name: 'Summer Open',
    status: 'ACTIVE',
    rounds: [
        {
            number: 2,
            tables: [
                {
                    tableNumber: 1,
                    trio: true,
                    teams: [
                        {
                            id: 'team-owls',
                            name: 'Owls',
                            players: [
                                { id: 'p-1', name: 'Anna' },
                                { id: 'p-2', name: 'Ben' },
                            ],
                        },
                        {
                            id: 'team-bears',
                            name: 'Bears',
                            players: [
                                { id: 'p-3', name: 'Cem' },
                                { id: 'p-4', name: 'Dora' },
                            ],
                        },
                        {
                            id: 'team-foxes',
                            name: 'Foxes',
                            players: [
                                { id: 'p-5', name: 'Eli' },
                                { id: 'p-6', name: 'Fiona' },
                            ],
                        },
                    ],
                },
                {
                    tableNumber: 2,
                    trio: false,
                    teams: [
                        {
                            id: 'team-wolves',
                            name: 'Wolves',
                            players: [
                                { id: 'p-7', name: 'Gus' },
                                { id: 'p-8', name: 'Hana' },
                            ],
                        },
                        {
                            id: 'team-hawks',
                            name: 'Hawks',
                            players: [
                                { id: 'p-9', name: 'Ivo' },
                                { id: 'p-10', name: 'Jana' },
                            ],
                        },
                    ],
                },
            ],
        },
        {
            number: 1,
            tables: [
                {
                    tableNumber: 1,
                    trio: false,
                    teams: [
                        {
                            id: 'team-hawks',
                            name: 'Hawks',
                            players: [{ id: 'p-9', name: 'Ivo' }],
                        },
                        {
                            id: 'team-owls',
                            name: 'Owls',
                            players: [{ id: 'p-1', name: 'Anna' }],
                        },
                    ],
                },
            ],
        },
    ],
};

describe('publicTournamentDetailDtoToDomain', () => {
    it('maps the tournament header and keeps the rounds in the order the server sent them', () => {
        const detail = publicTournamentDetailDtoToDomain(dto);

        expect(detail.id).toBe('t-2');
        expect(detail.name).toBe('Summer Open');
        expect(detail.status).toBe(TournamentStatus.ACTIVE);
        expect(detail.rounds.map((round) => round.number)).toEqual([2, 1]);
    });

    it('leaves totals and final ranking undefined before the tournament is published', () => {
        const detail = publicTournamentDetailDtoToDomain(dto);

        expect(detail.finalRanking).toBeUndefined();
        expect(at(at(detail.rounds, 0).tables, 0).teams[0]?.totals).toBeUndefined();
    });

    it('maps each table with its number and trio flag, keeping the table order', () => {
        const round2 = at(publicTournamentDetailDtoToDomain(dto).rounds, 0);

        expect(round2.tables.map((table) => table.tableNumber)).toEqual([1, 2]);
        expect(round2.tables.map((table) => table.trio)).toEqual([true, false]);
    });

    it('keeps the trio table with three teams and the normal table with two, in seat order', () => {
        const round2 = at(publicTournamentDetailDtoToDomain(dto).rounds, 0);
        const trioTable = at(round2.tables, 0);
        const normalTable = at(round2.tables, 1);

        expect(trioTable.teams.map((team) => team.name)).toEqual(['Owls', 'Bears', 'Foxes']);
        expect(normalTable.teams.map((team) => team.name)).toEqual(['Wolves', 'Hawks']);
    });

    it('maps each team with its id, name and players in order', () => {
        const round2 = at(publicTournamentDetailDtoToDomain(dto).rounds, 0);
        const owls = at(at(round2.tables, 0).teams, 0);

        expect(owls).toEqual({
            id: 'team-owls',
            name: 'Owls',
            players: [
                { id: 'p-1', name: 'Anna' },
                { id: 'p-2', name: 'Ben' },
            ],
        });
    });

    it('maps round 1 with its single table and one-player teams', () => {
        const round1 = at(publicTournamentDetailDtoToDomain(dto).rounds, 1);

        expect(round1).toEqual({
            number: 1,
            tables: [
                {
                    tableNumber: 1,
                    trio: false,
                    teams: [
                        { id: 'team-hawks', name: 'Hawks', players: [{ id: 'p-9', name: 'Ivo' }] },
                        { id: 'team-owls', name: 'Owls', players: [{ id: 'p-1', name: 'Anna' }] },
                    ],
                },
            ],
        });
    });
});

/**
 * The shared contract fixture: the backend's integration test asserts its API answers
 * exactly `response`; here the same wire data runs through the real zod schema and mapper.
 */
const publishedFixture = JSON.parse(
    readFileSync(
        resolve(
            __dirname,
            '../../../../../../../contract-fixtures/public-tournament-published.json',
        ),
        'utf8',
    ),
) as { response: unknown };

describe('publicTournamentDetailDtoToDomain for a published tournament', () => {
    const published = publicTournamentDetailDtoToDomain(
        publicTournamentDetailDtoSchema.parse(publishedFixture.response),
    );

    it('maps a trio table team to its table totals', () => {
        const trioTable = at(at(published.rounds, 0).tables, 1);
        const wolves = at(trioTable.teams, 2);

        expect(wolves.name).toBe('Wolves');
        expect(wolves.totals).toEqual({ victoryPoints: 0, tichuPoints: 120 });
    });

    it('maps a two-team table team to its table totals', () => {
        const falcons = at(at(at(published.rounds, 1).tables, 0).teams, 0);

        expect(falcons.name).toBe('Falcons');
        expect(falcons.totals).toEqual({ victoryPoints: 8, tichuPoints: 240 });
    });

    it('maps the final ranking in order, keeping shared ranks', () => {
        const ranking = published.finalRanking ?? [];

        expect(ranking.map((entry) => entry.rank)).toEqual([1, 2, 3, 3, 5]);
        expect(ranking.map((entry) => entry.teamName)).toEqual([
            'Falcons',
            'Ravens',
            'Badgers',
            'Owls',
            'Wolves',
        ]);
    });
});

describe('publicTournamentDtoToDomain', () => {
    it('maps a public tournament DTO to the public domain shape', () => {
        const publicDto: PublicTournamentDto = {
            id: 't-2',
            name: 'Summer Open',
            status: 'ACTIVE',
            teamCount: 3,
        };

        expect(publicTournamentDtoToDomain(publicDto)).toEqual({
            id: 't-2',
            name: 'Summer Open',
            status: TournamentStatus.ACTIVE,
            teamCount: 3,
        });
    });
});
