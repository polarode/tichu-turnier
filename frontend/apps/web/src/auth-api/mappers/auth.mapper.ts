import type { AdminCredentials, AdminRegistration, AdminUser } from '@/domain/admin-user.domain.ts';
import type {
    AdminDto,
    CredentialsRequestDto,
    RegistrationRequestDto,
} from '@/auth-api/dtos/auth.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function adminDtoToDomain(dto: AdminDto): AdminUser {
    return {
        id: dto.id,
        email: dto.email,
        role: dto.role,
    };
}

/** Domain credentials → wire payload; keeps the DTO out of the hooks. */
export function adminCredentialsToDto(credentials: AdminCredentials): CredentialsRequestDto {
    return {
        email: credentials.email,
        password: credentials.password,
    };
}

/**
 * Domain registration → wire payload. The invite token is omitted entirely when absent
 * so a plain registration never carries an `inviteToken` field.
 */
export function adminRegistrationToDto(registration: AdminRegistration): RegistrationRequestDto {
    return {
        email: registration.email,
        password: registration.password,
        ...(registration.inviteToken ? { inviteToken: registration.inviteToken } : {}),
    };
}
