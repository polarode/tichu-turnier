import type { Round, TableOverview } from '@/domain/round.domain.ts';
import {
    mapSeated,
    recordedGameDtoToDomain,
    scheduledGameDtoToDomain,
    tableConfirmationDtoToDomain,
} from '@/scoring-api/mappers/scoring.mapper.ts';
import type { RoundDto, TableOverviewDto } from '@/tournament-api/dtos/round.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function tableOverviewDtoToDomain(dto: TableOverviewDto): TableOverview {
    return {
        tableNumber: dto.tableNumber,
        teamIds: mapSeated(dto.teamIds, (teamId) => teamId),
        trio: dto.trio,
        confirmation: tableConfirmationDtoToDomain(dto),
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
