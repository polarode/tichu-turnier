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
    'ADMIN_STATUS_CONFLICT',
    'INVITE_INVALID',
    'INVITE_EXPIRED',
    'INVITE_EMAIL_MISMATCH',
    'INVITE_STATUS_CONFLICT',
    'LINK_ALREADY_REVEALED',
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export function isErrorCode(value: unknown): value is ErrorCode {
    return typeof value === 'string' && (ERROR_CODES as readonly string[]).includes(value);
}
