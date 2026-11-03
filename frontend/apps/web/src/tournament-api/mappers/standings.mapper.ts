import type { StandingsEntry } from '@/domain/standings.domain.ts';
import type { StandingsEntryDto } from '@/tournament-api/dtos/standings.dto.ts';

/** Pure mapping function. Unit-tested in isolation (see `__tests__`). */
export function standingsEntryDtoToDomain(dto: StandingsEntryDto): StandingsEntry {
    return {
        rank: dto.rank,
        teamId: dto.teamId,
        teamName: dto.teamName,
        gamesPlayed: dto.gamesPlayed,
        victoryPoints: dto.victoryPoints,
        tichuPoints: dto.tichuPoints,
    };
}
