import { describe, expect, it } from 'vitest';

import { roundDtoToDomain } from '@/tournament-api/mappers/round.mapper.ts';
import type { RoundDto } from '@/tournament-api/dtos/round.dto.ts';

const dto: RoundDto = {
    roundNumber: 1,
    tables: [
        {
            tableNumber: 1,
            teamIds: ['team-a', 'team-b'],
            trio: false,
            gameCount: 4,
            schedule: [1, 2, 3, 4].map((gameNumber) => ({ gameNumber, teamIds: ['team-a', 'team-b'] })),
            games: [
                {
                    gameNumber: 2,
                    version: 1,
                    beschiss: true,
                    teams: [
                        {
                            teamId: 'team-a',
                            baseScore: 55,
                            players: [
                                { playerId: 'a1', position: 1, call: 'SMALL', bombs: 0, tichuSuccess: true },
                                { playerId: 'a2', position: 3, call: 'NONE', bombs: 0, tichuSuccess: false },
                            ],
                        },
                        {
                            teamId: 'team-b',
                            baseScore: 45,
                            players: [
                                { playerId: 'b1', position: 2, call: 'NONE', bombs: 0, tichuSuccess: false },
                                { playerId: 'b2', position: 4, call: 'NONE', bombs: 0, tichuSuccess: false },
                            ],
                        },
                    ],
                    outcome: {
                        tichuSuccesses: ['a1'],
                        doubleWinTeam: null,
                        teamPoints: { 'team-a': 155, 'team-b': 45 },
                        victoryPoints: { 'team-a': 2, 'team-b': 0 },
                    },
                },
            ],
        },
    ],
};

describe('roundDtoToDomain', () => {
    it('maps each table with its game count and entered games', () => {
        const round = roundDtoToDomain(dto);

        expect(round.number).toBe(1);
        const [table] = round.tables;
        expect(table?.teamIds).toEqual(['team-a', 'team-b']);
        expect(table?.trio).toBe(false);
        expect(table?.schedule[3]).toEqual({ gameNumber: 4, teamIds: ['team-a', 'team-b'] });
        expect(table?.gameCount).toBe(4);
        expect(table?.games).toHaveLength(1);
        expect(table?.games[0]?.gameNumber).toBe(2);
        expect(table?.games[0]?.beschiss).toBe(true);
        expect(table?.games[0]?.outcome.teamPoints).toEqual({ 'team-a': 155, 'team-b': 45 });
    });
});

describe('roundDtoToDomain with a trio table', () => {
    it('keeps all three seated teams in seat order and the server schedule', () => {
        const round = roundDtoToDomain({
            roundNumber: 2,
            tables: [
                {
                    tableNumber: 1,
                    teamIds: ['team-c', 'team-a', 'team-b'],
                    trio: true,
                    gameCount: 6,
                    schedule: [
                        { gameNumber: 1, teamIds: ['team-c', 'team-a'] },
                        { gameNumber: 2, teamIds: ['team-c', 'team-b'] },
                        { gameNumber: 3, teamIds: ['team-a', 'team-b'] },
                        { gameNumber: 4, teamIds: ['team-c', 'team-a'] },
                        { gameNumber: 5, teamIds: ['team-c', 'team-b'] },
                        { gameNumber: 6, teamIds: ['team-a', 'team-b'] },
                    ],
                    games: [],
                },
            ],
        });

        const [table] = round.tables;
        expect(table?.trio).toBe(true);
        expect(table?.teamIds).toEqual(['team-c', 'team-a', 'team-b']);
        expect(table?.gameCount).toBe(6);
        expect(table?.schedule[2]).toEqual({ gameNumber: 3, teamIds: ['team-a', 'team-b'] });
    });
});
