/** Centralized TanStack Query keys so invalidation stays consistent across features. */
export const QueryKeys = {
    SESSION: 'session',
    TOURNAMENTS: 'tournaments',
} as const;

/** Key for a single tournament; the list key is `[QueryKeys.TOURNAMENTS]`. */
export const tournamentQueryKey = (id: string) => [QueryKeys.TOURNAMENTS, id] as const;
