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
            // TanStack runs this hook-level onSuccess before the mutate() caller's
            // callbacks, so the eviction has settled before /team's loader starts
            // its /me fetch — swapping that order would cancel the in-flight fetch
            // (CancelledError on the route).
            queryClient.removeQueries({ queryKey: teamOverviewQueryKey() });
        },
    });
}

/**
 * Query options for the session-guarded team overview (`GET /api/team/me`). The
 * httpOnly cookie is the only session store — the cache entry mirrors it for the
 * route guard and the overview screen. A 401 or 403 means there is no usable
 * team session (403 when an admin session lives in the same browser without a
 * team cookie); callers bounce to `/team/login` instead of retrying.
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
 * Whether a failure on a `/api/team/**` call means "no usable team session in
 * this browser". A 401 is the missing/expired team cookie; a 403 is the same
 * situation when the same browser holds an admin session — the backend rejects
 * `/api/team/**` as authenticated-but-not-allowed rather than unauthenticated.
 * Callers bounce to `/team/login` on either.
 */
export function isMissingTeamSession(error: unknown): error is ApiError {
    return error instanceof ApiError && (error.status === 401 || error.status === 403);
}

/**
 * Resolves the team overview through the QueryClient for a route guard. A
 * missing team session ([[isMissingTeamSession]]) resolves to null so the guard
 * can redirect to the neutral login prompt; any other failure propagates.
 */
export async function ensureTeamOverview(queryClient: QueryClient) {
    try {
        return await queryClient.ensureQueryData(teamOverviewQueryOptions);
    } catch (error) {
        if (isMissingTeamSession(error)) return null;
        throw error;
    }
}
