import { describe, expect, it } from 'vitest';

import type { GameWriteInput } from '@/domain/game.domain.ts';
import type { RecordedGameDto, TeamTableDto } from '@/scoring-api/dtos/scoring.dto.ts';
import {
    gameWriteInputToDto,
    recordedGameDtoToDomain,
    teamTableDtoToDomain,
} from '@/scoring-api/mappers/scoring.mapper.ts';

const tableDto: TeamTableDto = {
    tournamentId: 't-1',
    roundNumber: 2,
    tableNumber: 3,
    locked: false,
    allowGrandTichu: true,
    teams: [
        {
            teamId: 'team-a',
            teamName: 'Team Alpha',
            players: [
                { playerId: 'a1', name: 'Alice' },
                { playerId: 'a2', name: 'Anna' },
            ],
        },
        {
            teamId: 'team-b',
            teamName: 'Team Beta',
            players: [
                { playerId: 'b1', name: 'Bob' },
                { playerId: 'b2', name: 'Ben' },
            ],
        },
    ],
    trio: false,
    schedule: [1, 2, 3, 4].map((gameNumber) => ({ gameNumber, teamIds: ['team-a', 'team-b'] })),
};

const trioTableDto: TeamTableDto = {
    ...tableDto,
    teams: [
        ...tableDto.teams,
        {
            teamId: 'team-c',
            teamName: 'Team Gamma',
            players: [
                { playerId: 'c1', name: 'Cleo' },
                { playerId: 'c2', name: 'Carl' },
            ],
        },
    ],
    trio: true,
    schedule: [
        { gameNumber: 1, teamIds: ['team-a', 'team-b'] },
        { gameNumber: 2, teamIds: ['team-a', 'team-c'] },
        { gameNumber: 3, teamIds: ['team-b', 'team-c'] },
        { gameNumber: 4, teamIds: ['team-a', 'team-b'] },
        { gameNumber: 5, teamIds: ['team-a', 'team-c'] },
        { gameNumber: 6, teamIds: ['team-b', 'team-c'] },
    ],
};

const gameDto: RecordedGameDto = {
    gameNumber: 1,
    version: 4,
    beschiss: true,
    teams: [
        {
            teamId: 'team-a',
            baseScore: 55,
            players: [
                { playerId: 'a1', position: 1, call: 'SMALL', bombs: 0, tichuSuccess: true },
                { playerId: 'a2', position: 3, call: 'NONE', bombs: 1, tichuSuccess: false },
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
};

describe('teamTableDtoToDomain', () => {
    it('maps the table, matchup and roster', () => {
        const table = teamTableDtoToDomain(tableDto);

        expect(table.tournamentId).toBe('t-1');
        expect(table.roundNumber).toBe(2);
        expect(table.tableNumber).toBe(3);
        expect(table.allowGrandTichu).toBe(true);
        expect(table.teams[0].teamName).toBe('Team Alpha');
        expect(table.teams[1].players[1]).toEqual({ playerId: 'b2', name: 'Ben' });
        expect(table.trio).toBe(false);
        expect(table.schedule).toHaveLength(4);
    });

    it('maps a trio table with all three seated teams and its six-game schedule', () => {
        const table = teamTableDtoToDomain(trioTableDto);

        expect(table.trio).toBe(true);
        expect(table.teams.map((team) => team.teamName)).toEqual(['Team Alpha', 'Team Beta', 'Team Gamma']);
        expect(table.schedule[1]).toEqual({ gameNumber: 2, teamIds: ['team-a', 'team-c'] });
        expect(table.schedule[5]).toEqual({ gameNumber: 6, teamIds: ['team-b', 'team-c'] });
    });
});

describe('recordedGameDtoToDomain', () => {
    it('maps raw facts, the per-player success flag and the derived outcome', () => {
        const game = recordedGameDtoToDomain(gameDto);

        expect(game.gameNumber).toBe(1);
        expect(game.version).toBe(4);
        expect(game.beschiss).toBe(true);
        expect(game.teams[0].players[0]).toEqual({
            playerId: 'a1',
            position: 1,
            call: 'SMALL',
            bombs: 0,
            tichuSuccess: true,
        });
        expect(game.outcome.teamPoints).toEqual({ 'team-a': 155, 'team-b': 45 });
        expect(game.outcome.victoryPoints['team-a']).toBe(2);
    });
});

describe('gameWriteInputToDto', () => {
    it('maps the raw-facts write payload one-to-one (no totals, ADR 0004)', () => {
        const input: GameWriteInput = {
            gameNumber: 2,
            beschiss: false,
            version: null,
            teams: [
                {
                    teamId: 'team-a',
                    baseScore: 60,
                    players: [
                        { playerId: 'a1', position: 1, call: 'GRAND', bombs: 2 },
                        { playerId: 'a2', position: 4, call: 'NONE', bombs: 0 },
                    ],
                },
                {
                    teamId: 'team-b',
                    baseScore: 40,
                    players: [
                        { playerId: 'b1', position: 2, call: 'NONE', bombs: 0 },
                        { playerId: 'b2', position: 3, call: 'NONE', bombs: 0 },
                    ],
                },
            ],
        };

        expect(gameWriteInputToDto(input)).toEqual({
            gameNumber: 2,
            beschiss: false,
            version: null,
            teams: [
                {
                    teamId: 'team-a',
                    baseScore: 60,
                    players: [
                        { playerId: 'a1', position: 1, call: 'GRAND', bombs: 2 },
                        { playerId: 'a2', position: 4, call: 'NONE', bombs: 0 },
                    ],
                },
                {
                    teamId: 'team-b',
                    baseScore: 40,
                    players: [
                        { playerId: 'b1', position: 2, call: 'NONE', bombs: 0 },
                        { playerId: 'b2', position: 3, call: 'NONE', bombs: 0 },
                    ],
                },
            ],
        });
    });
});
