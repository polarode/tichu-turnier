import { describe, expect, it } from 'vitest';

import type { Matchup, RecordedGame, TeamTable, TeamTableTeam } from '@/domain/game.domain.ts';
import type { ScoringBonuses } from '@/domain/tournament.domain.ts';
import {
    complementBaseScore,
    doubleWinTeam,
    emptyFormState,
    fromRecordedGame,
    gameWinner,
    isCompleteResult,
    nextFinishPosition,
    teamPoints,
    victoryPoints,
    toWriteInput,
    withSlot,
    type GameFormState,
} from '@/features/scoring/game-form.model.ts';

const table: TeamTable = {
    tournamentId: 't-1',
    roundNumber: 1,
    tableNumber: 1,
    confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
    grandTichuBonus: 200,
    doubleWinBonus: 200,
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

describe('teamPoints totals', () => {
    const totals = (state: GameFormState, bonuses: ScoringBonuses) =>
        teamPoints(state, bonuses).map((team) => team.total);

    const normalBonuses = { grandTichuBonus: 200, doubleWinBonus: 200 } as const;

    it('adds a successful small-Tichu bonus to the caller team', () => {
        const state: GameFormState = {
            positions: [1, 3, 2, 4],
            calls: ['SMALL', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55,
            beschiss: false,
        };
        expect(totals(state, normalBonuses)).toEqual([155, 45]);
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
        expect(totals(state, normalBonuses)).toEqual([-200, 200]);
    });

    it('counts a grand Tichu with the tournament bonus, here 100', () => {
        const state: GameFormState = {
            positions: [1, 3, 2, 4],
            calls: ['GRAND', 'NONE', 'GRAND', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 50,
            beschiss: false,
        };
        // team 0's grand succeeded: 50 + 100; team 1's failed: 50 - 100.
        expect(totals(state, { ...normalBonuses, grandTichuBonus: 100 })).toEqual([150, -50]);
    });

    it('awards the +200 double-win bonus and forces base 0/0', () => {
        const state: GameFormState = {
            positions: [1, 2, null, null],
            calls: ['NONE', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55, // ignored on a double win
            beschiss: false,
        };
        expect(totals(state, normalBonuses)).toEqual([200, 0]);
    });

    it('counts a double win with the tournament bonus, here 100', () => {
        const state: GameFormState = {
            positions: [null, null, 2, 1],
            calls: ['NONE', 'NONE', 'NONE', 'SMALL'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 50,
            beschiss: false,
        };
        // team 1 took 1+2: 0 base + 100 small Tichu + 100 double win.
        expect(totals(state, { ...normalBonuses, doubleWinBonus: 100 })).toEqual([0, 200]);
    });
});

describe('teamPoints', () => {
    const normalBonuses = { grandTichuBonus: 200, doubleWinBonus: 200 } as const;

    it('has no bonus sources for either team when nobody called', () => {
        const state: GameFormState = {
            positions: [1, 3, 2, 4],
            calls: ['NONE', 'NONE', 'NONE', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55,
            beschiss: false,
        };
        expect(teamPoints(state, normalBonuses)).toEqual([
            { base: 55, bonuses: [], bonus: 0, total: 55 },
            { base: 45, bonuses: [], bonus: 0, total: 45 },
        ]);
    });

    it('lists a failed and a successful small Tichu per team, in slot order', () => {
        const state: GameFormState = {
            positions: [2, 3, 1, 4],
            calls: ['NONE', 'SMALL', 'SMALL', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 55,
            beschiss: false,
        };
        // slot 1 (team 0) failed: 55 - 100 = -45. slot 2 (team 1) finished 1st: 45 + 100 = 145.
        expect(teamPoints(state, normalBonuses)).toEqual([
            {
                base: 55,
                bonuses: [{ kind: 'SMALL_TICHU', slot: 1, success: false, points: -100 }],
                bonus: -100,
                total: -45,
            },
            {
                base: 45,
                bonuses: [{ kind: 'SMALL_TICHU', slot: 2, success: true, points: 100 }],
                bonus: 100,
                total: 145,
            },
        ]);
    });

    it('leaves Tichu calls out until someone has finished first', () => {
        const state: GameFormState = {
            positions: [null, 2, null, null],
            calls: ['SMALL', 'NONE', 'GRAND', 'NONE'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 50,
            beschiss: false,
        };
        expect(teamPoints(state, normalBonuses)).toEqual([
            { base: 50, bonuses: [], bonus: 0, total: 50 },
            { base: 50, bonuses: [], bonus: 0, total: 50 },
        ]);
    });

    it('nets a failed grand Tichu against the double-win bonus to 0 for the double-win team', () => {
        const state: GameFormState = {
            positions: [null, null, 1, 2],
            calls: ['NONE', 'NONE', 'NONE', 'GRAND'],
            bombs: [0, 0, 0, 0],
            baseScoreTeam0: 50,
            beschiss: false,
        };
        // team 1 double win: base forced 0. slot 3 called grand but finished 2nd: -200,
        // then the +200 double-win bonus. Total 0.
        expect(teamPoints(state, normalBonuses)[1]).toEqual({
            base: 0,
            bonuses: [
                { kind: 'GRAND_TICHU', slot: 3, success: false, points: -200 },
                { kind: 'DOUBLE_WIN', points: 200 },
            ],
            bonus: 0,
            total: 0,
        });
    });
});

describe('gameWinner', () => {
    const state = (positions: GameFormState['positions']): GameFormState => ({
        positions,
        calls: ['NONE', 'NONE', 'NONE', 'NONE'],
        bombs: [0, 0, 0, 0],
        baseScoreTeam0: 50,
        beschiss: false,
    });

    it('is the team ahead on points', () => {
        expect(gameWinner(state([2, 4, 1, 3]), [40, 60])).toBe(1);
    });

    it('is nobody on a tie', () => {
        expect(gameWinner(state([1, 3, 2, 4]), [50, 50])).toBeNull();
    });

    it('is the double-win team even when a failed call levels the points', () => {
        expect(gameWinner(state([null, null, 2, 1]), [0, 0])).toBe(1);
    });
});

describe('isCompleteResult', () => {
    it('accepts all four positions as a permutation of 1..4', () => {
        expect(isCompleteResult([2, 4, 1, 3])).toBe(true);
    });

    it('accepts a double win with exactly positions 1 and 2 assigned to one team', () => {
        expect(isCompleteResult([null, null, 2, 1])).toBe(true);
    });

    it('rejects an incomplete normal game', () => {
        expect(isCompleteResult([1, 3, 2, null])).toBe(false);
    });

    it('rejects a double win with a third position also assigned', () => {
        expect(isCompleteResult([1, 2, 3, null])).toBe(false);
        expect(isCompleteResult([1, 2, null, 3])).toBe(false);
    });
});

describe('victoryPoints', () => {
    const state = (positions: GameFormState['positions']): GameFormState => ({
        positions,
        calls: ['NONE', 'NONE', 'NONE', 'NONE'],
        bombs: [0, 0, 0, 0],
        baseScoreTeam0: 50,
        beschiss: false,
    });

    it('is null while the result is incomplete', () => {
        expect(victoryPoints(state([1, 3, 2, null]), [50, 50])).toBeNull();
    });

    it('gives the team ahead on points 2 and the other 0 on a normal win', () => {
        expect(victoryPoints(state([1, 3, 2, 4]), [60, 40])).toEqual([2, 0]);
    });

    it('gives each team 1 on a tie', () => {
        expect(victoryPoints(state([1, 3, 2, 4]), [50, 50])).toEqual([1, 1]);
    });

    it('gives the double-win team 3 even when a failed call levels the points', () => {
        expect(victoryPoints(state([null, null, 2, 1]), [0, 0])).toEqual([0, 3]);
        expect(victoryPoints(state([1, 2, null, null]), [0, 0])).toEqual([3, 0]);
    });

    it('is null on a double win with a third position also assigned', () => {
        expect(victoryPoints(state([1, 2, 3, null]), [0, 0])).toBeNull();
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

    it("builds the payload for the selected game's matchup, e.g. Alpha vs Gamma at a trio table", () => {
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
                        {
                            playerId: 'a1',
                            position: 1,
                            call: 'GRAND',
                            bombs: 2,
                            tichuSuccess: true,
                        },
                        {
                            playerId: 'a2',
                            position: 4,
                            call: 'NONE',
                            bombs: 0,
                            tichuSuccess: false,
                        },
                    ],
                },
                {
                    teamId: 'team-b',
                    baseScore: 40,
                    players: [
                        {
                            playerId: 'b1',
                            position: 2,
                            call: 'NONE',
                            bombs: 0,
                            tichuSuccess: false,
                        },
                        {
                            playerId: 'b2',
                            position: 3,
                            call: 'NONE',
                            bombs: 0,
                            tichuSuccess: false,
                        },
                    ],
                },
            ],
            correction: null,
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
                        {
                            playerId: 'b2',
                            position: 3,
                            call: 'NONE',
                            bombs: 0,
                            tichuSuccess: false,
                        },
                        {
                            playerId: 'b1',
                            position: 2,
                            call: 'NONE',
                            bombs: 1,
                            tichuSuccess: false,
                        },
                    ],
                },
                {
                    teamId: 'team-a',
                    baseScore: 60,
                    players: [
                        {
                            playerId: 'a2',
                            position: 4,
                            call: 'NONE',
                            bombs: 0,
                            tichuSuccess: false,
                        },
                        {
                            playerId: 'a1',
                            position: 1,
                            call: 'SMALL',
                            bombs: 0,
                            tichuSuccess: true,
                        },
                    ],
                },
            ],
            correction: null,
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
