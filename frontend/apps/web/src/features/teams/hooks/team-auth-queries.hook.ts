import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { teamOverviewQueryKey } from '@/shared/constants/query-keys.ts';
import type { TeamSession } from '@/domain/team-session.domain.ts';

/**
 * Exchanges a raw access-link token for the httpOnly team session. Every rejection —
 * unknown, revoked, expired or rate-limited — arrives as the same failure (ADR 0001),
 * so callers treat errors uniformly and never retry.
 */
export function useExchangeAccessLink() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (token: string): Promise<TeamSession> => TeamAuthRepository.exchange(token),
        onSuccess: () => {
            // A successful exchange changes the session state: a previously cached
            // overview (or a failed check) is stale by definition, so drop it and
            // let the /team guard fetch /me fresh.
            queryClient.removeQueries({ queryKey: teamOverviewQueryKey() });
        },
    });
}

/**
 * Query options for the session-guarded team overview (`GET /api/team/me`). The
 * httpOnly cookie is the only session store — the cache entry mirrors it for the
 * route guard and the overview screen. A 401 means the session is gone; callers
 * bounce to `/team/login` instead of retrying.
 */
export const teamOverviewQueryOptions = {
    queryKey: teamOverviewQueryKey(),
    queryFn: () => TeamAuthRepository.me(),
    retry: false,
    // The guard and the screen share one cache entry; a short window avoids a
    // duplicate `/me` fetch while staying close to the cookie's state.
    staleTime: 30_000,
};

/**
 * Resolves the team overview through the QueryClient for a route guard. A 401
 * resolves to null so the guard can redirect to the neutral login prompt; any
 * other failure propagates as an error.
 */
export async function ensureTeamOverview(queryClient: QueryClient) {
    try {
        return await queryClient.ensureQueryData(teamOverviewQueryOptions);
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
    }
}
