import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type {
    AdminCredentials,
    AdminRegistration,
    AdminUser,
} from '@/domain/admin-user.domain.ts';
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
 * Registers an account. Registration is no longer a sign-in: without an invite the
 * backend creates a `PENDING` account and returns no session, so the hook resolves to
 * `null` and the caller sends the user to the awaiting-approval state. With a valid
 * invite the account is `ACTIVE`; the register endpoint still returns no session, so
 * the UI composes register + login with the same credentials and resolves to the admin.
 */
export function useRegisterAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (registration: AdminRegistration): Promise<AdminUser | null> => {
            await AuthRepository.register(registration);
            if (!registration.inviteToken) return null;
            return AuthRepository.login({
                email: registration.email,
                password: registration.password,
            });
        },
        onSuccess: (admin) => {
            if (admin) queryClient.setQueryData(sessionQueryKey(), admin);
        },
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
