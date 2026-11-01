import { describe, expect, it } from 'vitest';

import { roundDtoToDomain } from '@/tournament-api/mappers/round.mapper.ts';
import type { RoundDto } from '@/tournament-api/dtos/round.dto.ts';

const dto: RoundDto = {
    roundNumber: 1,
    tables: [
        {
            tableNumber: 1,
            homeTeamId: 'team-a',
            awayTeamId: 'team-b',
            gameCount: 4,
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
        expect(table?.homeTeamId).toBe('team-a');
        expect(table?.gameCount).toBe(4);
        expect(table?.games).toHaveLength(1);
        expect(table?.games[0]?.gameNumber).toBe(2);
        expect(table?.games[0]?.beschiss).toBe(true);
        expect(table?.games[0]?.outcome.teamPoints).toEqual({ 'team-a': 155, 'team-b': 45 });
    });
});
