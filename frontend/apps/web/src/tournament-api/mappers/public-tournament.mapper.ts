import type {
    PublicPlayer,
    PublicRound,
    PublicTable,
    PublicTeam,
    PublicTournamentDetail,
} from '@/domain/tournament.domain.ts';
import type {
    PublicPlayerDto,
    PublicRoundDto,
    PublicTableDto,
    PublicTeamDto,
    PublicTournamentDetailDto,
} from '@/tournament-api/dtos/public-tournament.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function publicPlayerDtoToDomain(dto: PublicPlayerDto): PublicPlayer {
    return { id: dto.id, name: dto.name };
}

export function publicTeamDtoToDomain(dto: PublicTeamDto): PublicTeam {
    return {
        id: dto.id,
        name: dto.name,
        players: dto.players.map(publicPlayerDtoToDomain),
    };
}

export function publicTableDtoToDomain(dto: PublicTableDto): PublicTable {
    return {
        tableNumber: dto.tableNumber,
        trio: dto.trio,
        teams: dto.teams.map(publicTeamDtoToDomain),
    };
}

export function publicRoundDtoToDomain(dto: PublicRoundDto): PublicRound {
    return {
        number: dto.number,
        tables: dto.tables.map(publicTableDtoToDomain),
    };
}

export function publicTournamentDetailDtoToDomain(
    dto: PublicTournamentDetailDto,
): PublicTournamentDetail {
    return {
        id: dto.id,
        name: dto.name,
        status: dto.status,
        rounds: dto.rounds.map(publicRoundDtoToDomain),
    };
}
