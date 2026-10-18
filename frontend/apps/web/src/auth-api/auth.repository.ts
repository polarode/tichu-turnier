import {
    adminCredentialsToDto,
    adminDtoToDomain,
    adminRegistrationToDto,
} from '@/auth-api/mappers/auth.mapper.ts';
import { adminDtoSchema } from '@/auth-api/dtos/auth.dto.ts';
import type {
    AdminCredentials,
    AdminRegistration,
    AdminUser,
} from '@/domain/admin-user.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the auth endpoints and wire DTOs. Each function parses the
 * response with zod and maps it to a domain `AdminUser`; the session itself lives in an
 * httpOnly cookie the browser attaches automatically.
 */
async function register(registration: AdminRegistration): Promise<AdminUser> {
    const { data } = await apiClient.post<unknown>(
        '/api/auth/register',
        adminRegistrationToDto(registration),
    );
    return adminDtoToDomain(adminDtoSchema.parse(data));
}

async function login(credentials: AdminCredentials): Promise<AdminUser> {
    const { data } = await apiClient.post<unknown>(
        '/api/auth/login',
        adminCredentialsToDto(credentials),
    );
    return adminDtoToDomain(adminDtoSchema.parse(data));
}

/** Rotates the session cookie and returns the current admin; 401 when not signed in. */
async function refresh(): Promise<AdminUser> {
    const { data } = await apiClient.post<unknown>('/api/auth/refresh');
    return adminDtoToDomain(adminDtoSchema.parse(data));
}

async function logout(): Promise<void> {
    await apiClient.post('/api/auth/logout');
}

export const AuthRepository = {
    register,
    login,
    refresh,
    logout,
};
