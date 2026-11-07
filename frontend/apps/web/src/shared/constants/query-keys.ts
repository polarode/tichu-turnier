/** Centralized TanStack Query keys so invalidation stays consistent across features. */
export const QueryKeys = {
    SESSION: 'session',
    TOURNAMENTS: 'tournaments',
    TEAMS: 'teams',
    ROUNDS: 'rounds',
    STANDINGS: 'standings',
    ADMIN_ACCOUNTS: 'admin-accounts',
    ADMIN_INVITES: 'admin-invites',
    TEAM_ME: 'team-me',
    TEAM_CURRENT_TABLE: 'team-current-table',
    TEAM_GAMES: 'team-games',
    TEAM_FINAL_RANKING: 'team-final-ranking',
} as const;

/** Key for the team's Final Ranking (`GET /api/team/me/final-ranking`, published tournaments only). */
export const teamFinalRankingQueryKey = () => [QueryKeys.TEAM_FINAL_RANKING] as const;

/** Key for the current admin session. */
export const sessionQueryKey = () => [QueryKeys.SESSION] as const;

/** Key for a single tournament; the list key is `[QueryKeys.TOURNAMENTS]`. */
export const tournamentQueryKey = (id: string) => [QueryKeys.TOURNAMENTS, id] as const;

/** Key for a tournament's team list. */
export const teamsQueryKey = (tournamentId: string) => [QueryKeys.TEAMS, tournamentId] as const;

/** Key for a single team within a tournament. */
export const teamQueryKey = (tournamentId: string, teamId: string) =>
    [QueryKeys.TEAMS, tournamentId, teamId] as const;

/** Key for a tournament's rounds list. */
export const roundsQueryKey = (tournamentId: string) => [QueryKeys.ROUNDS, tournamentId] as const;

/** Key for a tournament's admin-only standings. */
export const standingsQueryKey = (tournamentId: string) => [QueryKeys.STANDINGS, tournamentId] as const;

/** Key for the owner console's admin-account list. */
export const adminAccountsQueryKey = () => [QueryKeys.ADMIN_ACCOUNTS] as const;

/** Key for the owner console's invite list. */
export const adminInvitesQueryKey = () => [QueryKeys.ADMIN_INVITES] as const;

/** Key for the team session's own overview (`GET /api/team/me`). */
export const teamOverviewQueryKey = () => [QueryKeys.TEAM_ME] as const;

/** Key for the team's current (in-progress) round table. */
export const teamCurrentTableQueryKey = () => [QueryKeys.TEAM_CURRENT_TABLE] as const;

/** Key for the games entered at one round table (team-scoped read). */
export const teamGamesQueryKey = (tournamentId: string, roundNumber: number, tableNumber: number) =>
    [QueryKeys.TEAM_GAMES, tournamentId, roundNumber, tableNumber] as const;

