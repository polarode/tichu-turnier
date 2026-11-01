import type { Round, TableOverview } from '@/domain/round.domain.ts';
import { recordedGameDtoToDomain } from '@/scoring-api/mappers/scoring.mapper.ts';
import type { RoundDto, TableOverviewDto } from '@/tournament-api/dtos/round.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function tableOverviewDtoToDomain(dto: TableOverviewDto): TableOverview {
    return {
        tableNumber: dto.tableNumber,
        homeTeamId: dto.homeTeamId,
        awayTeamId: dto.awayTeamId,
        gameCount: dto.gameCount,
        games: dto.games.map(recordedGameDtoToDomain),
    };
}

export function roundDtoToDomain(dto: RoundDto): Round {
    return {
        number: dto.roundNumber,
        tables: dto.tables.map(tableOverviewDtoToDomain),
    };
}
