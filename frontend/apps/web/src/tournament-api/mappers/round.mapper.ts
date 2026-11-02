import type { Round, TableOverview } from '@/domain/round.domain.ts';
import { recordedGameDtoToDomain, scheduledGameDtoToDomain } from '@/scoring-api/mappers/scoring.mapper.ts';
import type { RoundDto, TableOverviewDto } from '@/tournament-api/dtos/round.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function tableOverviewDtoToDomain(dto: TableOverviewDto): TableOverview {
    return {
        tableNumber: dto.tableNumber,
        teamIds: [...dto.teamIds],
        trio: dto.trio,
        gameCount: dto.gameCount,
        schedule: dto.schedule.map(scheduledGameDtoToDomain),
        games: dto.games.map(recordedGameDtoToDomain),
    };
}

export function roundDtoToDomain(dto: RoundDto): Round {
    return {
        number: dto.roundNumber,
        tables: dto.tables.map(tableOverviewDtoToDomain),
    };
}
