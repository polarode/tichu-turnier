import {
    adminAccountDtoToDomain,
    inviteDtoToDomain,
    issuedInviteDtoToDomain,
} from '@/admin-api/mappers/admin.mapper.ts';
import {
    adminAccountDtoSchema,
    adminAccountListDtoSchema,
    inviteDtoSchema,
    inviteListDtoSchema,
    issuedInviteDtoSchema,
} from '@/admin-api/dtos/admin.dto.ts';
import type { AdminAccount, AdminInvite, IssuedInvite } from '@/domain/admin-account.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the owner-console endpoints and wire DTOs. Each function
 * parses the response with zod and maps it to a domain model; the backend enforces the
 * owner role, so a non-owner gets a 403 the caller surfaces as a forbidden state.
 */
async function listAccounts(): Promise<AdminAccount[]> {
    const { data } = await apiClient.get<unknown>('/api/admins');
    return adminAccountListDtoSchema.parse(data).map(adminAccountDtoToDomain);
}

async function approve(id: string): Promise<AdminAccount> {
    const { data } = await apiClient.post<unknown>(`/api/admins/${id}/approve`);
    return adminAccountDtoToDomain(adminAccountDtoSchema.parse(data));
}

async function reject(id: string): Promise<void> {
    await apiClient.post(`/api/admins/${id}/reject`);
}

async function disable(id: string): Promise<AdminAccount> {
    const { data } = await apiClient.post<unknown>(`/api/admins/${id}/disable`);
    return adminAccountDtoToDomain(adminAccountDtoSchema.parse(data));
}

async function reactivate(id: string): Promise<AdminAccount> {
    const { data } = await apiClient.post<unknown>(`/api/admins/${id}/reactivate`);
    return adminAccountDtoToDomain(adminAccountDtoSchema.parse(data));
}

async function listInvites(): Promise<AdminInvite[]> {
    const { data } = await apiClient.get<unknown>('/api/invites');
    return inviteListDtoSchema.parse(data).map(inviteDtoToDomain);
}

async function createInvite(email: string): Promise<IssuedInvite> {
    const { data } = await apiClient.post<unknown>('/api/invites', { email });
    return issuedInviteDtoToDomain(issuedInviteDtoSchema.parse(data));
}

async function revokeInvite(id: string): Promise<AdminInvite> {
    const { data } = await apiClient.post<unknown>(`/api/invites/${id}/revoke`);
    return inviteDtoToDomain(inviteDtoSchema.parse(data));
}

export const AdminRepository = {
    listAccounts,
    approve,
    reject,
    disable,
    reactivate,
    listInvites,
    createInvite,
    revokeInvite,
};
