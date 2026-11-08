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
    TOO_MANY_ATTEMPTS: 'error.tooManyAttempts',
    UNAUTHENTICATED: 'error.unauthenticated',
    FORBIDDEN: 'error.forbidden',
    TOURNAMENT_NOT_EDITABLE: 'error.tournamentNotEditable',
    DUPLICATE_TEAM_NAME: 'error.duplicateTeamName',
    DUPLICATE_PLAYER_NAME: 'error.duplicatePlayerName',
    DUPLICATE_TEAM_PLAYERS: 'error.duplicateTeamPlayers',
    TEAM_LIMIT_REACHED: 'error.teamLimitReached',
    MAX_TEAMS_BELOW_TEAM_COUNT: 'error.maxTeamsBelowTeamCount',
    TOTAL_ROUNDS_BELOW_ROUND_COUNT: 'error.totalRoundsBelowRoundCount',
    ADMIN_STATUS_CONFLICT: 'error.adminStatusConflict',
    INVITE_INVALID: 'error.inviteInvalid',
    INVITE_EXPIRED: 'error.inviteExpired',
    INVITE_STATUS_CONFLICT: 'error.inviteStatusConflict',
    LINK_ALREADY_REVEALED: 'error.linkAlreadyRevealed',
    TEAM_ACCESS_INVALID: 'error.teamAccessInvalid',
    TOURNAMENT_NOT_STARTABLE: 'error.tournamentNotStartable',
    INSUFFICIENT_TEAMS: 'error.insufficientTeams',
    TOURNAMENT_NOT_STARTED: 'error.tournamentNotStarted',
    TOURNAMENT_NOT_ACTIVE: 'error.tournamentNotActive',
    ROUND_INCOMPLETE: 'error.roundIncomplete',
    TOTAL_ROUNDS_REACHED: 'error.totalRoundsReached',
    ROUNDS_REMAINING: 'error.roundsRemaining',
    TOURNAMENT_NOT_COMPLETED: 'error.tournamentNotCompleted',
    RESULTS_NOT_PUBLISHED: 'error.resultsNotPublished',
    GAME_VALIDATION_INVALID: 'error.gameValidationInvalid',
    GAME_ALREADY_EXISTS: 'error.gameAlreadyExists',
    GAME_CONCURRENT_MODIFICATION: 'error.gameConcurrentModification',
    GAME_CONFIRMED: 'error.gameConfirmed',
    GAME_NOT_FOUND: 'error.gameNotFound',
    TABLE_INCOMPLETE: 'error.tableIncomplete',
    TABLE_COMPLETED: 'error.tableCompleted',
};

/** The catalog key for a backend error code; never returns a missing key. */
export function errorMessageKey(code: ApiErrorCode): TranslationKey {
    return ERROR_MESSAGE_KEYS[code];
}
