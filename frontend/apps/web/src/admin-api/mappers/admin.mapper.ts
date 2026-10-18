import type { AdminAccount, AdminInvite, IssuedInvite } from '@/domain/admin-account.domain.ts';
import type {
    AdminAccountDto,
    InviteDto,
    IssuedInviteDto,
} from '@/admin-api/dtos/admin.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function adminAccountDtoToDomain(dto: AdminAccountDto): AdminAccount {
    return {
        id: dto.id,
        email: dto.email,
        status: dto.status,
        role: dto.role,
        createdAt: new Date(dto.createdAt),
        approvedBy: dto.approvedBy,
        approvedAt: dto.approvedAt === null ? null : new Date(dto.approvedAt),
        invitedBy: dto.invitedBy,
        invitedAt: dto.invitedAt === null ? null : new Date(dto.invitedAt),
    };
}

export function inviteDtoToDomain(dto: InviteDto): AdminInvite {
    return {
        id: dto.id,
        email: dto.email,
        status: dto.status,
        createdBy: dto.createdBy,
        createdAt: new Date(dto.createdAt),
        expiresAt: new Date(dto.expiresAt),
        spentAt: dto.spentAt === null ? null : new Date(dto.spentAt),
    };
}

export function issuedInviteDtoToDomain(dto: IssuedInviteDto): IssuedInvite {
    return {
        ...inviteDtoToDomain(dto),
        token: dto.token,
        link: dto.link,
    };
}
