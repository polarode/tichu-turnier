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
 * Result of a registration. A plain registration is `pending`; an invited one is
 * `active` when the follow-up sign-in succeeds, or `signInFailed` when the account
 * activated but the session could not be established.
 */
export type RegistrationOutcome =
    | { kind: 'pending' }
    | { kind: 'active'; admin: AdminUser }
    | { kind: 'signInFailed' };

/**
 * Registers an account. Registration is no longer a sign-in: without an invite the
 * backend creates a `PENDING` account and returns no session, so the hook resolves to
 * `pending` and the caller sends the user to the awaiting-approval state. With a valid
 * invite the account is `ACTIVE`; the register endpoint still returns no session, so
 * the UI composes register + login with the same credentials. If that login fails the
 * account stays active, so the outcome is `signInFailed` rather than an error the user
 * should retry registration for.
 */
export function useRegisterAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (registration: AdminRegistration): Promise<RegistrationOutcome> => {
            await AuthRepository.register(registration);
            if (!registration.inviteToken) return { kind: 'pending' };
            try {
                const admin = await AuthRepository.login({
                    email: registration.email,
                    password: registration.password,
                });
                return { kind: 'active', admin };
            } catch {
                return { kind: 'signInFailed' };
            }
        },
        onSuccess: (outcome) => {
            if (outcome.kind === 'active') {
                queryClient.setQueryData(sessionQueryKey(), outcome.admin);
            }
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
