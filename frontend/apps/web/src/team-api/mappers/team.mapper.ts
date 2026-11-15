import type { Player } from '@/domain/player.domain.ts';
import type {
    Team,
    TeamWithdrawal,
    TeamWriteInput,
    WithdrawalInput,
} from '@/domain/team.domain.ts';
import type {
    PlayerDto,
    TeamDto,
    TeamWriteRequestDto,
    WithdrawalDto,
    WithdrawalRequestDto,
} from '@/team-api/dtos/team.dto.ts';

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
        withdrawal: dto.withdrawal ? withdrawalDtoToDomain(dto.withdrawal) : null,
    };
}

export function withdrawalDtoToDomain(dto: WithdrawalDto): TeamWithdrawal {
    return {
        reason: dto.reason,
        compensation: dto.compensation,
        roundNumber: dto.roundNumber,
        withdrawnAt: new Date(dto.withdrawnAt),
    };
}

/** Domain withdrawal input → wire payload; keeps the DTO out of the hooks. */
export function withdrawalInputToDto(input: WithdrawalInput): WithdrawalRequestDto {
    return {
        reason: input.reason,
        compensation: input.compensation,
    };
}

/** Domain team → editable input, used prefill an edit form. */
export function teamToWriteInput(team: Team): TeamWriteInput {
    return {
        name: team.name,
        playerOneName: team.players[0].name,
        playerTwoName: team.players[1].name,
    };
}

/** Domain write input → wire payload; keeps the DTO out of the hooks. */
export function teamWriteInputToDto(input: TeamWriteInput): TeamWriteRequestDto {
    return {
        name: input.name,
        playerOneName: input.playerOneName,
        playerTwoName: input.playerTwoName,
    };
}
