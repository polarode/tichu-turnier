import type { ApiErrorCode } from '@/shared/api/api-error.ts';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

/**
 * Maps every stable backend error code to the catalog key that localizes it. The
 * record is exhaustive over {@link ApiErrorCode}, so adding a code to the vocabulary
 * without a message is a compile error.
 */
export const ERROR_MESSAGE_KEYS: Record<ApiErrorCode, TranslationKey> = {
    UNKNOWN: 'error.unknown',
    NOT_FOUND: 'error.notFound',
    INVALID_INPUT: 'error.invalidInput',
    EMAIL_ALREADY_REGISTERED: 'error.emailAlreadyRegistered',
    INVALID_CREDENTIALS: 'error.invalidCredentials',
    ACCOUNT_PENDING: 'error.accountPending',
    ACCOUNT_DISABLED: 'error.accountDisabled',
    UNAUTHENTICATED: 'error.unauthenticated',
    FORBIDDEN: 'error.forbidden',
    TOURNAMENT_NOT_EDITABLE: 'error.tournamentNotEditable',
    DUPLICATE_TEAM_NAME: 'error.duplicateTeamName',
    DUPLICATE_PLAYER_NAME: 'error.duplicatePlayerName',
    DUPLICATE_TEAM_PLAYERS: 'error.duplicateTeamPlayers',
    TEAM_LIMIT_REACHED: 'error.teamLimitReached',
    MAX_TEAMS_BELOW_TEAM_COUNT: 'error.maxTeamsBelowTeamCount',
    ADMIN_STATUS_CONFLICT: 'error.adminStatusConflict',
    INVITE_INVALID: 'error.inviteInvalid',
    INVITE_EXPIRED: 'error.inviteExpired',
    INVITE_EMAIL_MISMATCH: 'error.inviteEmailMismatch',
    INVITE_STATUS_CONFLICT: 'error.inviteStatusConflict',
    LINK_ALREADY_REVEALED: 'error.linkAlreadyRevealed',
    TEAM_ACCESS_INVALID: 'error.teamAccessInvalid',
    TOURNAMENT_NOT_STARTABLE: 'error.tournamentNotStartable',
    INSUFFICIENT_TEAMS: 'error.insufficientTeams',
    PAIRING_UNSUPPORTED: 'error.pairingUnsupported',
};

/** The catalog key for a backend error code; never returns a missing key. */
export function errorMessageKey(code: ApiErrorCode): TranslationKey {
    return ERROR_MESSAGE_KEYS[code];
}
