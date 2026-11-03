/**
 * Stable machine-readable error codes returned in the `code` member of the backend's
 * RFC 7807 problem responses. Mirrors the backend `ErrorCode` enum; the frontend
 * localizes against these, so they must never be renamed without a backend change.
 */
export const ERROR_CODES = [
    'NOT_FOUND',
    'INVALID_INPUT',
    'EMAIL_ALREADY_REGISTERED',
    'INVALID_CREDENTIALS',
    'ACCOUNT_PENDING',
    'ACCOUNT_DISABLED',
    'UNAUTHENTICATED',
    'FORBIDDEN',
    'TOURNAMENT_NOT_EDITABLE',
    'DUPLICATE_TEAM_NAME',
    'DUPLICATE_PLAYER_NAME',
    'DUPLICATE_TEAM_PLAYERS',
    'TEAM_LIMIT_REACHED',
    'MAX_TEAMS_BELOW_TEAM_COUNT',
    /** A Total Rounds change below the rounds already drawn. */
    'TOTAL_ROUNDS_BELOW_ROUND_COUNT',
    'ADMIN_STATUS_CONFLICT',
    'INVITE_INVALID',
    'INVITE_EXPIRED',
    'INVITE_STATUS_CONFLICT',
    'LINK_ALREADY_REVEALED',

    /** A team access link cannot be exchanged; all causes share this one code (ADR 0001). */
    'TEAM_ACCESS_INVALID',

    /** Starting a tournament that is not in the `setup` state (already started or completed). */
    'TOURNAMENT_NOT_STARTABLE',

    /** A tournament with fewer than two teams cannot be started. */
    'INSUFFICIENT_TEAMS',

    /** Standings requested for a tournament still in setup. */
    'TOURNAMENT_NOT_STARTED',

    // --- Next round (S7b) ---
    /** An action that needs an active tournament (e.g. the next round) on one that is not. */
    'TOURNAMENT_NOT_ACTIVE',
    /** The next round can only start once every table of the current round is completed. */
    'ROUND_INCOMPLETE',
    /** The Total Rounds are reached: the tournament can only be finished. */
    'TOTAL_ROUNDS_REACHED',

    // --- Scoring (S5) ---
    /** A submitted game failed the scoring engine's validation; field-anchored where sensible. */
    'GAME_VALIDATION_INVALID',
    /** POST to a game slot that already holds a game (the other team got there first). */
    'GAME_ALREADY_EXISTS',
    /** A PUT lost the optimistic-lock race; reload and retry. */
    'GAME_CONCURRENT_MODIFICATION',
    /** The game's table is confirming or completed; its games are frozen (ADR 0006). */
    'GAME_CONFIRMED',
    /** No game at the addressed slot (PUT before create, or unknown round/table). */
    'GAME_NOT_FOUND',

    // --- Confirmation (S6) ---
    /** Confirming before every game of the table is entered. */
    'TABLE_INCOMPLETE',
    /** Retracting on a completed table; its confirmations are final. */
    'TABLE_COMPLETED',
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export function isErrorCode(value: unknown): value is ErrorCode {
    return typeof value === 'string' && (ERROR_CODES as readonly string[]).includes(value);
}
