/** Centralized TanStack Query keys so invalidation stays consistent across features. */
export const QueryKeys = {
    SESSION: 'session',
    TOURNAMENTS: 'tournaments',
    TEAMS: 'teams',
    ADMIN_ACCOUNTS: 'admin-accounts',
    ADMIN_INVITES: 'admin-invites',
} as const;

/** Key for the current admin session. */
export const sessionQueryKey = () => [QueryKeys.SESSION] as const;

/** Key for a single tournament; the list key is `[QueryKeys.TOURNAMENTS]`. */
export const tournamentQueryKey = (id: string) => [QueryKeys.TOURNAMENTS, id] as const;

/** Key for a tournament's team list. */
export const teamsQueryKey = (tournamentId: string) => [QueryKeys.TEAMS, tournamentId] as const;

/** Key for a single team within a tournament. */
export const teamQueryKey = (tournamentId: string, teamId: string) =>
    [QueryKeys.TEAMS, tournamentId, teamId] as const;

/** Key for the owner console's admin-account list; a status filter is a suffix. */
export const adminAccountsQueryKey = () => [QueryKeys.ADMIN_ACCOUNTS] as const;

/** Key for the owner console's invite list. */
export const adminInvitesQueryKey = () => [QueryKeys.ADMIN_INVITES] as const;
