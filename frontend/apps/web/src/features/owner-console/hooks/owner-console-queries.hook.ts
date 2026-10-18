import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { AdminRepository } from '@/admin-api/admin.repository.ts';
import { adminAccountsQueryKey, adminInvitesQueryKey } from '@/shared/constants/query-keys.ts';

/**
 * TanStack Query hooks for the owner console. Components call these; they never touch
 * the repository directly. Account mutations invalidate the account list so an
 * approved/rejected/disabled row moves between statuses immediately.
 *
 * Both queries take an `enabled` flag: the console only runs its owner-only requests
 * for an owner, so a non-owner never even fires a call that would 403.
 */
export function useAdminAccounts(enabled = true) {
    return useQuery({
        queryKey: adminAccountsQueryKey(),
        queryFn: () => AdminRepository.listAccounts(),
        enabled,
    });
}

export function useApproveAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => AdminRepository.approve(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAccountsQueryKey() }),
    });
}

export function useRejectAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => AdminRepository.reject(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAccountsQueryKey() }),
    });
}

export function useDisableAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => AdminRepository.disable(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAccountsQueryKey() }),
    });
}

export function useReactivateAdmin() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => AdminRepository.reactivate(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: adminAccountsQueryKey() }),
    });
}

export function useInvites(enabled = true) {
    return useQuery({
        queryKey: adminInvitesQueryKey(),
        queryFn: () => AdminRepository.listInvites(),
        enabled,
    });
}

export function useCreateInvite() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (email: string) => AdminRepository.createInvite(email),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: adminInvitesQueryKey() }),
    });
}

export function useRevokeInvite() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => AdminRepository.revokeInvite(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: adminInvitesQueryKey() }),
    });
}
