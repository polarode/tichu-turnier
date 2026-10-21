import type { RevealedAccessLink } from '@/domain/access-link.domain.ts';
import type { AccessLinkRevealDto } from '@/team-api/dtos/access-link.dto.ts';

/** Pure mapping function, unit-tested in isolation (see `__tests__`). */
export function accessLinkRevealDtoToDomain(dto: AccessLinkRevealDto): RevealedAccessLink {
    return {
        token: dto.token,
        link: dto.link,
    };
}
