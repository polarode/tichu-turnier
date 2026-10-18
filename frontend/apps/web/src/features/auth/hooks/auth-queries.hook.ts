import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { CredentialsRequestDto } from '@/auth-api/dtos/auth.dto.ts';
import type { AdminUser } from '@/domain/admin-user.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { QueryKeys } from '@/shared/constants/query-keys.ts';

/**
 * Resolves the current admin by rotating the session cookie server-side. A 401 is a
 * normal "not signed in" outcome, not an error: it resolves to null so guarded screens
 * can redirect instead of showing a failure.
 */
export function useSession() {
    return useQuery({
        queryKey: [QueryKeys.SESSION],
        queryFn: async (): Promise<AdminUser | null> => {
            try {
                return await AuthRepository.refresh();
            } catch (error) {
                if (error instanceof ApiError && error.status === 401) return null;
                throw error;
            }
        },
        retry: false,
    });
}

export function useRegisterAdmin() {
    return useMutation({
        mutationFn: (request: CredentialsRequestDto) => AuthRepository.register(request),
    });
}

export function useLoginAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (request: CredentialsRequestDto) => AuthRepository.login(request),
        onSuccess: (admin) => queryClient.setQueryData([QueryKeys.SESSION], admin),
    });
}

export function useLogoutAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: () => AuthRepository.logout(),
        onSuccess: () => queryClient.setQueryData([QueryKeys.SESSION], null),
    });
}
