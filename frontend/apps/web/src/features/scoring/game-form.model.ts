import type {
    GameWriteInput,
    Matchup,
    PlayerResultInput,
    RecordedGame,
    TichuCall,
} from '@/domain/game.domain.ts';
import type { ScoringBonuses } from '@/domain/tournament.domain.ts';

/**
 * Client-side model for the game entry grid. Mirrors v1's input model: four
 * players in two teams, per-player finish position / Tichu call / bomb count, one
 * base score for team 1 with the complement derived. All derived figures here are
 * display-only — the server recomputes and is authoritative (ADR 0004); the form
 * submits raw facts only.
 */

const SMALL_TICHU_BONUS = 100;

/** The four grid slots, in a fixed order: team 0's two players, then team 1's. */
export const PLAYER_SLOTS = [0, 1, 2, 3] as const;
export type PlayerSlot = (typeof PLAYER_SLOTS)[number];

/** Which team (0 or 1) a slot belongs to: slots 0,1 → team 0; slots 2,3 → team 1. */
export function teamOfSlot(slot: PlayerSlot): 0 | 1 {
    return slot < 2 ? 0 : 1;
}

/** Returns a copy of the 4-tuple with one slot replaced — keeps the tuple type. */
export function withSlot<T>(quad: Quad<T>, slot: PlayerSlot, value: T): Quad<T> {
    const next: Quad<T> = [quad[0], quad[1], quad[2], quad[3]];
    next[slot] = value;
    return next;
}

/** A fixed 4-slot tuple, indexed by [PlayerSlot]. */
type Quad<T> = [T, T, T, T];

export interface GameFormState {
    positions: Quad<number | null>;
    calls: Quad<TichuCall>;
    bombs: Quad<number>;
    /** Team 0's base score; team 1's is the 100-complement (or 0 on a double win). */
    baseScoreTeam0: number;
    beschiss: boolean;
}

/** The pristine state for a fresh game. */
export function emptyFormState(): GameFormState {
    return {
        positions: [null, null, null, null],
        calls: ['NONE', 'NONE', 'NONE', 'NONE'],
        bombs: [0, 0, 0, 0],
        baseScoreTeam0: 50,
        beschiss: false,
    };
}

/**
 * The next finish position to assign when a player's "Finish" button is pressed:
 * the lowest of 1..4 not already taken. Returns null when all are used.
 */
export function nextFinishPosition(positions: (number | null)[]): number | null {
    const used = new Set(positions.filter((p): p is number => p !== null));
    for (let i = 1; i <= 4; i++) {
        if (!used.has(i)) return i;
    }
    return null;
}

/** The team (0 or 1) that holds both positions 1 and 2, or null. A double win. */
export function doubleWinTeam(positions: (number | null)[]): 0 | 1 | null {
    const slotOf = (pos: number) => positions.findIndex((p) => p === pos);
    const first = slotOf(1);
    const second = slotOf(2);
    if (first === -1 || second === -1) return null;
    const firstTeam = teamOfSlot(first as PlayerSlot);
    return firstTeam === teamOfSlot(second as PlayerSlot) ? firstTeam : null;
}

/**
 * Whether the finish positions form a complete result: a double win with exactly
 * positions 1 and 2 assigned to its winners, or all four players finished with
 * positions a permutation of 1..4. The form schema's positions rule (ADR 0004).
 */
export function isCompleteResult(positions: (number | null)[]): boolean {
    const assigned = positions.filter((p): p is number => p !== null);
    const sorted = [...assigned].sort().join();
    if (doubleWinTeam(positions) !== null) return sorted === '1,2';
    return sorted === '1,2,3,4';
}

/** Team 1's base score, derived from team 0's (or 0 on a double win). */
export function complementBaseScore(baseScoreTeam0: number, isDoubleWin: boolean): number {
    return isDoubleWin ? 0 : 100 - baseScoreTeam0;
}

/** One bonus a team earned or lost in this game; [points] is signed. */
export type BonusSource =
    | { kind: 'SMALL_TICHU' | 'GRAND_TICHU'; slot: PlayerSlot; success: boolean; points: number }
    | { kind: 'DOUBLE_WIN'; points: number };

/** A team's display points: the card [base], its bonus sources, their signed sum [bonus], and [total]. */
export interface TeamPoints {
    base: number;
    bonuses: BonusSource[];
    bonus: number;
    total: number;
}

/**
 * The per-team display points with their bonus sources, for [team0, team1]. Tichu
 * calls come first in slot order, then the double-win bonus. A call only counts once
 * someone has finished first, as until then it has neither succeeded nor failed.
 * Display-only — never
 * submitted; the server recomputes (ADR 0004).
 */
export function teamPoints(
    state: GameFormState,
    bonuses: ScoringBonuses,
): [TeamPoints, TeamPoints] {
    const dw = doubleWinTeam(state.positions);
    const teams: [TeamPoints, TeamPoints] = [
        { base: dw === null ? state.baseScoreTeam0 : 0, bonuses: [], bonus: 0, total: 0 },
        {
            base: dw === null ? complementBaseScore(state.baseScoreTeam0, false) : 0,
            bonuses: [],
            bonus: 0,
            total: 0,
        },
    ];
    const callers = state.positions.includes(1) ? PLAYER_SLOTS : [];
    for (const slot of callers) {
        const team = teamOfSlot(slot);
        const call = state.calls[slot];
        const success = state.positions[slot] === 1;
        if (call === 'SMALL') {
            teams[team].bonuses.push({
                kind: 'SMALL_TICHU',
                slot,
                success,
                points: success ? SMALL_TICHU_BONUS : -SMALL_TICHU_BONUS,
            });
        } else if (call === 'GRAND') {
            teams[team].bonuses.push({
                kind: 'GRAND_TICHU',
                slot,
                success,
                points: success ? bonuses.grandTichuBonus : -bonuses.grandTichuBonus,
            });
        }
    }
    if (dw !== null) teams[dw].bonuses.push({ kind: 'DOUBLE_WIN', points: bonuses.doubleWinBonus });
    for (const team of teams) {
        team.bonus = team.bonuses.reduce((sum, source) => sum + source.points, 0);
        team.total = team.base + team.bonus;
    }
    return teams;
}

/**
 * The team (0 or 1) that wins this game: the double-win team outright (even when
 * a failed call levels the [totals]), otherwise the team ahead on points; null
 * on a tie. Mirrors the server's Victory Points (ADR 0004).
 */
export function gameWinner(state: GameFormState, totals: [number, number]): 0 | 1 | null {
    const dw = doubleWinTeam(state.positions);
    if (dw !== null) return dw;
    return totals[0] === totals[1] ? null : totals[0] > totals[1] ? 0 : 1;
}

/**
 * The Victory Points each team gets for this game, for [team0, team1], or null
 * while the result is incomplete. A double win is 3 to the winners and 0 to the
 * other team even on levelled [totals]; otherwise the team ahead on points gets 2
 * and the other 0, and a tie gives 1 each. Mirrors the server (ADR 0004).
 */
export function victoryPoints(
    state: GameFormState,
    totals: [number, number],
): [number, number] | null {
    if (!isCompleteResult(state.positions)) return null;
    const dw = doubleWinTeam(state.positions);
    if (dw !== null) return dw === 0 ? [3, 0] : [0, 3];
    if (totals[0] === totals[1]) return [1, 1];
    return totals[0] > totals[1] ? [2, 0] : [0, 2];
}

/**
 * Builds the write payload from the grid state and the game's [matchup] (the two
 * playing teams and their rosters). Groups the four slots back into the two
 * teams; on a double win both base scores are 0 and the losing players carry no
 * position (ADR 0004). [version] null → create.
 */
export function toWriteInput(
    state: GameFormState,
    matchup: Matchup,
    gameNumber: number,
    version: number | null,
): GameWriteInput {
    const dw = doubleWinTeam(state.positions);
    const baseTeam0 = dw === null ? state.baseScoreTeam0 : 0;
    const baseTeam1 = dw === null ? complementBaseScore(state.baseScoreTeam0, false) : 0;

    return {
        gameNumber,
        beschiss: state.beschiss,
        version,
        teams: [
            {
                teamId: matchup[0].teamId,
                baseScore: baseTeam0,
                players: [
                    slotToPlayer(state, 0, matchup[0].players[0].playerId),
                    slotToPlayer(state, 1, matchup[0].players[1].playerId),
                ],
            },
            {
                teamId: matchup[1].teamId,
                baseScore: baseTeam1,
                players: [
                    slotToPlayer(state, 2, matchup[1].players[0].playerId),
                    slotToPlayer(state, 3, matchup[1].players[1].playerId),
                ],
            },
        ],
    };
}

function slotToPlayer(state: GameFormState, slot: PlayerSlot, playerId: string): PlayerResultInput {
    return {
        playerId,
        position: state.positions[slot],
        call: state.calls[slot],
        bombs: state.bombs[slot],
    };
}

/**
 * Rebuilds grid state from a stored game, for edit mode. Deliberately order-
 * independent: players are matched by id (not by the stored team/player order)
 * and the slot-0 team's base score is found by team id, so a stored game whose
 * `team1`/`team2` order differs from the [matchup]'s seat order still loads each
 * player and the base slider onto the correct slot. Do not replace
 * these id lookups with positional access.
 */
export function fromRecordedGame(game: RecordedGame, matchup: Matchup): GameFormState {
    const positions: Quad<number | null> = [null, null, null, null];
    const calls: Quad<TichuCall> = ['NONE', 'NONE', 'NONE', 'NONE'];
    const bombs: Quad<number> = [0, 0, 0, 0];

    const recordedById = new Map(
        game.teams.flatMap((team) => team.players.map((p) => [p.playerId, p] as const)),
    );
    for (const slot of PLAYER_SLOTS) {
        const team = matchup[teamOfSlot(slot)];
        const playerId = team.players[slot % 2 === 0 ? 0 : 1].playerId;
        const recorded = recordedById.get(playerId);
        if (recorded) {
            positions[slot] = recorded.position;
            calls[slot] = recorded.call;
            bombs[slot] = recorded.bombs;
        }
    }

    const team0 = game.teams.find((t) => t.teamId === matchup[0].teamId);
    return {
        positions,
        calls,
        bombs,
        baseScoreTeam0: team0?.baseScore ?? 50,
        beschiss: game.beschiss,
    };
}
