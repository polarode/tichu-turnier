import type { Organizers } from '@/domain/organizers.domain.ts';
import type { OrganizersDto } from '@/tournament-api/dtos/organizers.dto.ts';

/** Pure mapping function. Unit-tested in isolation (see `__tests__`). */
export function organizersDtoToDomain(dto: OrganizersDto): Organizers {
    return {
        lead: { email: dto.lead.email },
        coOrganizers: dto.coOrganizers.map((coOrganizer) => ({
            email: coOrganizer.email,
            addedAt: new Date(coOrganizer.addedAt),
        })),
    };
}
