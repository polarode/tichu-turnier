import type { AdminCredentials, AdminUser } from '@/domain/admin-user.domain.ts';
import type { AdminDto, CredentialsRequestDto } from '@/auth-api/dtos/auth.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function adminDtoToDomain(dto: AdminDto): AdminUser {
    return {
        id: dto.id,
        email: dto.email,
    };
}

/** Domain credentials → wire payload; keeps the DTO out of the hooks. */
export function adminCredentialsToDto(credentials: AdminCredentials): CredentialsRequestDto {
    return {
        email: credentials.email,
        password: credentials.password,
    };
}
