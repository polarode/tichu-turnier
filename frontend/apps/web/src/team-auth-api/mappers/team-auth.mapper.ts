import type { TeamSession } from '@/domain/team-session.domain.ts';
import type { TeamSessionDto } from '@/team-auth-api/dtos/team-auth.dto.ts';

/** Pure mapping function, unit-tested in isolation (see `__tests__`). */
export function teamSessionDtoToDomain(dto: TeamSessionDto): TeamSession {
    return {
        teamId: dto.teamId,
        tournamentId: dto.tournamentId,
        expiresAt: new Date(dto.expiresAt),
    };
}
