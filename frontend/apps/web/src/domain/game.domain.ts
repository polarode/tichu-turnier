/**
 * Domain models for the team scoring form (S5c, #101). Framework-free; the
 * `scoring-api` layer maps the wire DTOs onto these. Mirrors the backend's raw
 * facts (ADR 0004): the form submits only positions, calls, bombs and base
 * scores; everything else (success, double win, totals, victory points) is
 * derived by the server and returned in the outcome.
 */

import type { DoubleWinBonus, GrandTichuBonus } from '@/domain/tournament.domain.ts';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

/** A player's pre-deal announcement. Mirrors the backend `TichuCall` enum. */
export type TichuCall = 'NONE' | 'SMALL' | 'GRAND';

/**
 * Addresses one round table: the natural key the games endpoints take
 * (tournament, round number, table number). [TeamTable] is a structural
 * superset, so a loaded table can be passed wherever a [TableRef] is expected.
 */
export interface TableRef {
    tournamentId: string;
    roundNumber: number;
    tableNumber: number;
}

/** The teams seated at a table, in seat order: two, or three at the trio table. */
export type SeatedTeams<T> = [T, T] | [T, T, T];

/** One game of a table's schedule: its 1-based number and the two teams playing it. */
export interface ScheduledGame {
    gameNumber: number;
    teamIds: [string, string];
}

/**
 * A game that was never played because one of its teams withdrew (ADR 0011). Shown as
 * forfeited; it counts toward Victory and Tichu Points only. [awardedTeamId] is null when
 * nobody is awarded, and then both points are 0.
 */
export interface ForfeitGame {
    gameNumber: number;
    withdrawnTeamId: string;
    awardedTeamId: string | null;
    victoryPoints: number;
    tichuPoints: number;
}

/**
 * A table's confirmation state (ADR 0006): the derived [status] and the teams
 * that confirmed, in seat order. Games are read-only unless the table is PLAYING.
 */
export interface TableConfirmation {
    status: TableStatus;
    confirmedTeamIds: readonly string[];
}

/** Whether the table's games are frozen: someone confirmed, so nobody may edit (ADR 0006). */
export function isFrozen(confirmation: TableConfirmation): boolean {
    return confirmation.status !== TableStatus.PLAYING;
}

/** Whether every seated team confirmed: final, nothing at the table changes any more. */
export function isCompleted(confirmation: TableConfirmation): boolean {
    return confirmation.status === TableStatus.COMPLETED;
}

/**
 * The team's current table: which round and table it is entering, the seated
 * teams and their rosters, whether it is the trio table and who plays which
 * game, its [confirmation] state and what a grand Tichu call and a double win are
 * worth (so the form's live totals match the server). From `GET /api/team/me/current-table`.
 */
export interface TeamTable {
    tournamentId: string;
    roundNumber: number;
    tableNumber: number;
    confirmation: TableConfirmation;
    grandTichuBonus: GrandTichuBonus;
    doubleWinBonus: DoubleWinBonus;
    teams: SeatedTeams<TeamTableTeam>;
    trio: boolean;
    schedule: readonly ScheduledGame[];
    /** The table's Forfeit Games (ADR 0011); empty unless a seated team withdrew. */
    forfeits: readonly ForfeitGame[];
    /** The seated teams that withdrew; their games are forfeited, not played. */
    withdrawnTeamIds: readonly string[];
}

export interface TeamTableTeam {
    teamId: string;
    teamName: string;
    players: [TeamTablePlayer, TeamTablePlayer];
}

/** The two teams playing one game at a table (GLOSSARY.md: Matchup), in seat order. */
export type Matchup = [TeamTableTeam, TeamTableTeam];

/**
 * The matchup of game [gameNumber] at [table], resolved from the server schedule
 * (the frontend never re-derives the trio schedule). Throws for a game number the
 * schedule does not list — callers only ever pass numbers taken from it.
 */
export function matchupOf(table: TeamTable, gameNumber: number): Matchup {
    const scheduled = table.schedule.find((game) => game.gameNumber === gameNumber);
    const team = (teamId: string) => table.teams.find((seated) => seated.teamId === teamId);
    const first = scheduled && team(scheduled.teamIds[0]);
    const second = scheduled && team(scheduled.teamIds[1]);
    if (!first || !second)
        throw new Error(`Game ${gameNumber} is not on table ${table.tableNumber}'s schedule`);
    return [first, second];
}

/**
 * Whether a scheduled game is locked by a withdrawal (ADR 0011): one of its two teams
 * withdrew, so the game is a Forfeit Game or stays read-only, and nothing is entered for it.
 */
export function isLockedByWithdrawal(
    scheduled: ScheduledGame,
    withdrawnTeamIds: readonly string[],
): boolean {
    return scheduled.teamIds.some((teamId) => withdrawnTeamIds.includes(teamId));
}

/**
 * Whether game [gameNumber] is settled (ADR 0011): recorded or forfeited, so nothing is left
 * to enter for it. A game neither recorded nor forfeited is still missing.
 */
export function isSettled(
    gameNumber: number,
    games: readonly RecordedGame[],
    forfeits: readonly ForfeitGame[],
): boolean {
    return (
        games.some((game) => game.gameNumber === gameNumber) ||
        forfeits.some((forfeit) => forfeit.gameNumber === gameNumber)
    );
}

/** Whether [teamId] sits out game [gameNumber] at [table] (only ever true at a trio table). */
export function sitsOut(table: TeamTable, teamId: string, gameNumber: number): boolean {
    return !matchupOf(table, gameNumber).some((team) => team.teamId === teamId);
}

/** The name of the team seated at [table] as [teamId]; the id itself when it is not seated there. */
export function teamNameOf(table: TeamTable, teamId: string): string {
    return table.teams.find((team) => team.teamId === teamId)?.teamName ?? teamId;
}

/** The 0-based seat of [teamId] at [table] (A = 0, B = 1, C = 2); drives the seat colour. */
export function seatOf(table: TeamTable, teamId: string): number {
    return table.teams.findIndex((team) => team.teamId === teamId);
}

export interface TeamTablePlayer {
    playerId: string;
    name: string;
}

/** A player's raw facts as entered in the form (the write payload, per player). */
export interface PlayerResultInput {
    playerId: string;
    /** 1-based finish position; null when the player never finished (double-win loser). */
    position: number | null;
    call: TichuCall;
    bombs: number;
}

export interface TeamResultInput {
    teamId: string;
    baseScore: number;
    players: [PlayerResultInput, PlayerResultInput];
}

/** The transport-agnostic write payload for recording a game. */
export interface GameWriteInput {
    gameNumber: number;
    beschiss: boolean;
    /** PUT only — the optimistic-locking version the game was read with. */
    version: number | null;
    teams: [TeamResultInput, TeamResultInput];
}

/** The server-authoritative view of a stored game: raw facts + derived outcome. */
export interface RecordedGame {
    gameNumber: number;
    version: number;
    beschiss: boolean;
    teams: [RecordedTeam, RecordedTeam];
    outcome: GameOutcome;
    /** Set when an organizer corrected the game at a completed table (ADR 0010); null otherwise. */
    correction: GameCorrection | null;
}

/** The audit stamp of an organizer correction: who corrected the game and when. */
export interface GameCorrection {
    correctedBy: string;
    correctedAt: Date;
}

export interface RecordedTeam {
    teamId: string;
    baseScore: number;
    players: [RecordedPlayer, RecordedPlayer];
}

export interface RecordedPlayer {
    playerId: string;
    position: number | null;
    call: TichuCall;
    bombs: number;
    /** Derived server-side: a call made and finished first (ADR 0004). */
    tichuSuccess: boolean;
}

/**
 * The derived outcome of a game (ADR 0004): per-player tichu success, the
 * double-win team if any, and per-team total Tichu Points and Victory Points —
 * keyed by team id.
 */
export interface GameOutcome {
    tichuSuccesses: string[];
    doubleWinTeam: string | null;
    teamPoints: Record<string, number>;
    victoryPoints: Record<string, number>;
}

/** A team's total Tichu Points in a game; undefined when the team did not play it. */
export function teamPoints(game: RecordedGame, teamId: string): number | undefined {
    return game.outcome.teamPoints[teamId];
}

/** A team's Victory Points summed over [games]; a game it did not play adds nothing. */
export function totalVictoryPoints(games: readonly RecordedGame[], teamId: string): number {
    return games.reduce((sum, game) => sum + (game.outcome.victoryPoints[teamId] ?? 0), 0);
}
