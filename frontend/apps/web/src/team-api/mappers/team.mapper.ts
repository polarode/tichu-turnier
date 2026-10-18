import type { Player } from '@/domain/player.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { PlayerDto, TeamDto, TeamWriteRequestDto } from '@/team-api/dtos/team.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */
export function playerDtoToDomain(dto: PlayerDto): Player {
    return {
        id: dto.id,
        teamId: dto.teamId,
        name: dto.name,
    };
}

export function teamDtoToDomain(dto: TeamDto): Team {
    return {
        id: dto.id,
        tournamentId: dto.tournamentId,
        name: dto.name,
        players: [playerDtoToDomain(dto.players[0]), playerDtoToDomain(dto.players[1])],
    };
}

/** Domain → write payload, used when a form edits an existing team. */
export function teamToWriteRequestDto(team: Team): TeamWriteRequestDto {
    return {
        name: team.name,
        playerOneName: team.players[0].name,
        playerTwoName: team.players[1].name,
    };
}
