import { describe, expect, it } from 'vitest';

import type { Matchup, RecordedGame, TeamTable, TeamTableTeam } from '@/domain/game.domain.ts';
import {
    complementBaseScore,
    displayTotals,
    doubleWinTeam,
    emptyFormState,
    fromRecordedGame,
    nextFinishPosition,
    toWriteInput,
    withSlot,
    type GameFormState,
} from '@/features/scoring/game-form.model.ts';

const table: TeamTable = {
    tournamentId: 't-1',
    roundNumber: 1,
    tableNumber: 1,
    confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
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

/** A normal table's matchup: both seated teams play every game. */
const matchup: Matchup = [table.teams[0], table.teams[1]];

const gamma: TeamTableTeam = {
    teamId: 'team-c',
    teamName: 'Team Gamma',
    players: [
        { playerId: 'c1', name: 'Cleo' },
        { playerId: 'c2', name: 'Carl' },
    ],
};

describe('nextFinishPosition', () => {
    it('assigns the lowest unused position 1..4', () => {
        expect(nextFinishPosition([null, null, null, null])).toBe(1);
        expect(nextFinishPosition([1, null, 2, null])).toBe(3);
        expect(nextFinishPosition([2, null, null, null])).toBe(1);
    });

    it('returns null when all four are taken', () => {
        expect(nextFinishPosition([1, 2, 3, 4])).toBeNull();
    });
});

describe('doubleWinTeam', () => {
    it('detects a double win when positions 1 and 2 belong to the same team', () => {
        // slots 0,1 = team 0; a team-0 double win.
        expect(doubleWinTeam([1, 2, null, null])).toBe(0);
        expect(doubleWinTeam([2, 1, null, null])).toBe(0);
        // team-1 double win (slots 2,3).
        expect(doubleWinTeam([null, null, 1, 2])).toBe(1);
    });

    it('is null for a normal game where 1 and 2 are split across teams', () => {
        expect(doubleWinTeam([1, 3, 2, 4])).toBeNull();
    });

    it('is null until both leading positions are assigned', () => {
        expect(doubleWinTeam([1, null, null, null])).toBeNull();
    });
});

describe('complementBaseScore', () => {
    it('derives team 1 as the 100-complement in a normal game', () => {
        expect(complementBaseScore(55, false)).toBe(45);
        expect(complementBaseScore(-25, false)).toBe(125);
    });

    it('is 0 on a double win (base forced 0/0)', () => {
        expect(complementBaseScore(55, true)).toBe(0);
    });
});

describe('displayTotals', () => {
    it('adds a successful small-Tichu bonus to the caller team', () => {
        const state: GameFormState = {
            positions: [1, 3, 2, 4],
            calls: ['SMALL', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55,
            beschiss: false,
        };
        expect(displayTotals(state)).toEqual([155, 45]);
    });

    it('subtracts a failed grand-Tichu bonus', () => {
        const state: GameFormState = {
            positions: [3, 4, 1, 2],
            calls: ['GRAND', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 50,
            beschiss: false,
        };
        // team 1 took 1+2 -> double win, so both base scores are forced to 0.
        // team 0 called grand and did not finish first: 0 - 200 = -200.
        // team 1: base 0 + 200 double-win bonus = 200.
        expect(displayTotals(state)).toEqual([-200, 200]);
    });

    it('awards the +200 double-win bonus and forces base 0/0', () => {
        const state: GameFormState = {
            positions: [1, 2, null, null],
            calls: ['NONE', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55, // ignored on a double win
            beschiss: false,
        };
        expect(displayTotals(state)).toEqual([200, 0]);
    });
});

describe('withSlot', () => {
    it('replaces one slot and preserves the others', () => {
        expect(withSlot([null, null, null, null], 2, 1)).toEqual([null, null, 1, null]);
    });
});

describe('toWriteInput', () => {
    it('groups slots into the two teams and carries raw facts only', () => {
        const state: GameFormState = {
            positions: [1, 3, 2, 4],
            calls: ['SMALL', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 1, 0, 0],
            baseScoreTeam0: 55,
            beschiss: true,
        };
        const input = toWriteInput(state, matchup, 1, null);

        expect(input).toEqual({
            gameNumber: 1,
            beschiss: true,
            version: null,
            teams: [
                {
                    teamId: 'team-a',
                    baseScore: 55,
                    players: [
                        { playerId: 'a1', position: 1, call: 'SMALL', bombs: 0 },
                        { playerId: 'a2', position: 3, call: 'NONE', bombs: 1 },
                    ],
                },
                {
                    teamId: 'team-b',
                    baseScore: 45,
                    players: [
                        { playerId: 'b1', position: 2, call: 'NONE', bombs: 0 },
                        { playerId: 'b2', position: 4, call: 'NONE', bombs: 0 },
                    ],
                },
            ],
        });
    });

    it('forces base 0/0 and keeps losers position null on a double win', () => {
        const state: GameFormState = {
            positions: [1, 2, null, null],
            calls: ['NONE', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55,
            beschiss: false,
        };
        const input = toWriteInput(state, matchup, 2, 7);

        expect(input.version).toBe(7);
        expect(input.teams[0].baseScore).toBe(0);
        expect(input.teams[1].baseScore).toBe(0);
        expect(input.teams[1].players.map((p) => p.position)).toEqual([null, null]);
    });

    it('builds the payload for the selected game\'s matchup, e.g. Alpha vs Gamma at a trio table', () => {
        const input = toWriteInput(emptyFormState(), [table.teams[0], gamma], 2, null);

        expect(input.teams.map((team) => team.teamId)).toEqual(['team-a', 'team-c']);
        expect(input.teams[1].players.map((p) => p.playerId)).toEqual(['c1', 'c2']);
    });
});

describe('fromRecordedGame', () => {
    it('rebuilds grid state from a stored game, mapping by player id', () => {
        const game: RecordedGame = {
            gameNumber: 1,
            version: 3,
            beschiss: true,
            teams: [
                {
                    teamId: 'team-a',
                    baseScore: 60,
                    players: [
                        { playerId: 'a1', position: 1, call: 'GRAND', bombs: 2, tichuSuccess: true },
                        { playerId: 'a2', position: 4, call: 'NONE', bombs: 0, tichuSuccess: false },
                    ],
                },
                {
                    teamId: 'team-b',
                    baseScore: 40,
                    players: [
                        { playerId: 'b1', position: 2, call: 'NONE', bombs: 0, tichuSuccess: false },
                        { playerId: 'b2', position: 3, call: 'NONE', bombs: 0, tichuSuccess: false },
                    ],
                },
            ],
            outcome: {
                tichuSuccesses: ['a1'],
                doubleWinTeam: null,
                teamPoints: { 'team-a': 260, 'team-b': 40 },
                victoryPoints: { 'team-a': 2, 'team-b': 0 },
            },
        };

        expect(fromRecordedGame(game, matchup)).toEqual({
            positions: [1, 4, 2, 3],
            calls: ['GRAND', 'NONE', 'NONE', 'NONE'],
            bombs: [2, 0, 0, 0],
            baseScoreTeam0: 60,
            beschiss: true,
        });
    });

    it('keys by id, not position — stored team/player order may differ from the table', () => {
        // The stored game lists team-b first and, within each team, the second
        // roster player first. A positional rebuild would scramble the grid and
        // load team-b's base score into slot 0; keying by id must not.
        const game: RecordedGame = {
            gameNumber: 1,
            version: 3,
            beschiss: false,
            teams: [
                {
                    teamId: 'team-b',
                    baseScore: 40,
                    players: [
                        { playerId: 'b2', position: 3, call: 'NONE', bombs: 0, tichuSuccess: false },
                        { playerId: 'b1', position: 2, call: 'NONE', bombs: 1, tichuSuccess: false },
                    ],
                },
                {
                    teamId: 'team-a',
                    baseScore: 60,
                    players: [
                        { playerId: 'a2', position: 4, call: 'NONE', bombs: 0, tichuSuccess: false },
                        { playerId: 'a1', position: 1, call: 'SMALL', bombs: 0, tichuSuccess: true },
                    ],
                },
            ],
            outcome: {
                tichuSuccesses: ['a1'],
                doubleWinTeam: null,
                teamPoints: { 'team-a': 160, 'team-b': 40 },
                victoryPoints: { 'team-a': 2, 'team-b': 0 },
            },
        };

        // Slots follow the TABLE order (a1, a2, b1, b2), not the stored order,
        // and baseScoreTeam0 is team-a's 60 (slot-0 team), not team-b's 40.
        expect(fromRecordedGame(game, matchup)).toEqual({
            positions: [1, 4, 2, 3],
            calls: ['SMALL', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 1, 0],
            baseScoreTeam0: 60,
            beschiss: false,
        });
    });
});

describe('emptyFormState', () => {
    it('starts with no positions, no calls, no bombs and a 50/50 base', () => {
        expect(emptyFormState()).toEqual({
            positions: [null, null, null, null],
            calls: ['NONE', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 50,
            beschiss: false,
        });
    });
});
