import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { AdminCredentials, AdminUser } from '@/domain/admin-user.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { sessionQueryKey } from '@/shared/constants/query-keys.ts';

/**
 * Resolves the current admin by rotating the session cookie server-side. A 401 is a
 * normal "not signed in" outcome, not an error: it resolves to null so guarded screens
 * can redirect instead of showing a failure. The long `staleTime` keeps the rotating
 * refresh from firing on every mount.
 */
export function useSession() {
    return useQuery({
        queryKey: sessionQueryKey(),
        queryFn: async (): Promise<AdminUser | null> => {
            try {
                return await AuthRepository.refresh();
            } catch (error) {
                if (error instanceof ApiError && error.status === 401) return null;
                throw error;
            }
        },
        retry: false,
        staleTime: 5 * 60_000,
    });
}

export function useRegisterAdmin() {
    return useMutation({
        mutationFn: (credentials: AdminCredentials) => AuthRepository.register(credentials),
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
