import type { AdminUser } from '@/domain/admin-user.domain.ts';
import type { AdminDto } from '@/auth-api/dtos/auth.dto.ts';

/** Pure mapping function. Unit-tested in isolation (see `__tests__`). */
export function adminDtoToDomain(dto: AdminDto): AdminUser {
    return {
        id: dto.id,
        email: dto.email,
    };
}
