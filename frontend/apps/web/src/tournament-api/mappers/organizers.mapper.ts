import type { Organizers } from '@/domain/organizers.domain.ts';
import type { OrganizersDto } from '@/tournament-api/dtos/organizers.dto.ts';

/** Pure mapping function. Unit-tested in isolation (see `__tests__`). */
export function organizersDtoToDomain(dto: OrganizersDto): Organizers {
    return {
        lead: { email: dto.lead.email, disabled: dto.lead.disabled },
        coOrganizers: dto.coOrganizers.map((coOrganizer) => ({
            adminUserId: coOrganizer.adminUserId,
            email: coOrganizer.email,
            addedAt: new Date(coOrganizer.addedAt),
            disabled: coOrganizer.disabled,
        })),
    };
}
