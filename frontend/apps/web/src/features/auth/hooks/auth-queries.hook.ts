import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { AdminCredentials, AdminUser } from '@/domain/admin-user.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { sessionQueryKey } from '@/shared/constants/query-keys.ts';

/**
 * Resolves the current admin by rotating the session cookie server-side. A 401 is a
 * normal "not signed in" outcome, not an error: it resolves to null so guarded screens
 * can redirect instead of showing a failure.
 */
async function fetchSession(): Promise<AdminUser | null> {
    try {
        return await AuthRepository.refresh();
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
    }
}

/**
 * Query options shared by `useSession()` and the admin route guard's `beforeLoad`, so
 * both read one cache entry. The long `staleTime` keeps the rotating refresh from
 * firing on every mount.
 */
export const sessionQueryOptions = {
    queryKey: sessionQueryKey(),
    queryFn: fetchSession,
    retry: false,
    staleTime: 5 * 60_000,
};

/** The current admin session, or null when signed out. */
export function useSession() {
    return useQuery(sessionQueryOptions);
}

/**
 * Registers an account and signs it straight in. The register endpoint deliberately
 * returns no session (Phase 2), so the UI composes register + login; when invite-gated
 * onboarding (Phase 4) makes registration asynchronous this becomes a pending state.
 */
export function useRegisterAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (credentials: AdminCredentials) => {
            await AuthRepository.register(credentials);
            return AuthRepository.login(credentials);
        },
        onSuccess: (admin) => queryClient.setQueryData(sessionQueryKey(), admin),
    });
}

export function useLoginAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (credentials: AdminCredentials) => AuthRepository.login(credentials),
        onSuccess: (admin) => queryClient.setQueryData(sessionQueryKey(), admin),
    });
}

export function useLogoutAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: () => AuthRepository.logout(),
        onSuccess: () => queryClient.setQueryData(sessionQueryKey(), null),
    });
}
