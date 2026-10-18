import { adminDtoToDomain } from '@/auth-api/mappers/auth.mapper.ts';
import { adminDtoSchema, type CredentialsRequestDto } from '@/auth-api/dtos/auth.dto.ts';
import type { AdminUser } from '@/domain/admin-user.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the auth endpoints. Each function parses the response with
 * zod and maps it to a domain `AdminUser`; the session itself lives in an httpOnly
 * cookie the browser attaches automatically.
 */
async function register(request: CredentialsRequestDto): Promise<AdminUser> {
    const { data } = await apiClient.post<unknown>('/api/auth/register', request);
    return adminDtoToDomain(adminDtoSchema.parse(data));
}

async function login(request: CredentialsRequestDto): Promise<AdminUser> {
    const { data } = await apiClient.post<unknown>('/api/auth/login', request);
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
