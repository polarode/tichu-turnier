import type { Round, TableAssignment } from '@/domain/round.domain.ts';
import type { RoundDto, TableAssignmentDto } from '@/tournament-api/dtos/round.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function tableAssignmentDtoToDomain(dto: TableAssignmentDto): TableAssignment {
    return {
        tableNumber: dto.tableNumber,
        homeTeamId: dto.homeTeamId,
        awayTeamId: dto.awayTeamId,
    };
}

export function roundDtoToDomain(dto: RoundDto): Round {
    return {
        number: dto.roundNumber,
        tables: dto.tables.map(tableAssignmentDtoToDomain),
    };
}
