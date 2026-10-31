import type {
    GameOutcome,
    GameWriteInput,
    RecordedGame,
    RecordedPlayer,
    RecordedTeam,
    TeamTable,
    TeamTableTeam,
} from '@/domain/game.domain.ts';
import type {
    GameRequestDto,
    RecordedGameDto,
    TeamResultRequestDto,
    TeamTableDto,
} from '@/scoring-api/dtos/scoring.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */

/** Maps a 2-tuple element-wise, preserving the fixed pair shape the domain uses. */
function mapPair<T, R>(pair: readonly [T, T], f: (item: T) => R): [R, R] {
    return [f(pair[0]), f(pair[1])];
}

export function teamTableDtoToDomain(dto: TeamTableDto): TeamTable {
    return {
        tournamentId: dto.tournamentId,
        roundNumber: dto.roundNumber,
        tableNumber: dto.tableNumber,
        locked: dto.locked,
        allowGrandTichu: dto.allowGrandTichu,
        teams: mapPair(dto.teams, teamTableTeamDtoToDomain),
    };
}

function teamTableTeamDtoToDomain(dto: TeamTableDto['teams'][number]): TeamTableTeam {
    return {
        teamId: dto.teamId,
        teamName: dto.teamName,
        players: mapPair(dto.players, (p) => ({ playerId: p.playerId, name: p.name })),
    };
}

export function recordedGameDtoToDomain(dto: RecordedGameDto): RecordedGame {
    return {
        gameNumber: dto.gameNumber,
        version: dto.version,
        beschiss: dto.beschiss,
        teams: mapPair(dto.teams, recordedTeamDtoToDomain),
        outcome: outcomeDtoToDomain(dto.outcome),
    };
}

function recordedTeamDtoToDomain(dto: RecordedGameDto['teams'][number]): RecordedTeam {
    return {
        teamId: dto.teamId,
        baseScore: dto.baseScore,
        players: mapPair(dto.players, recordedPlayerDtoToDomain),
    };
}

function recordedPlayerDtoToDomain(dto: RecordedGameDto['teams'][number]['players'][number]): RecordedPlayer {
    return {
        playerId: dto.playerId,
        position: dto.position,
        call: dto.call,
        bombs: dto.bombs,
        tichuSuccess: dto.tichuSuccess,
    };
}

function outcomeDtoToDomain(dto: RecordedGameDto['outcome']): GameOutcome {
    return {
        tichuSuccesses: dto.tichuSuccesses,
        doubleWinTeam: dto.doubleWinTeam,
        teamPoints: dto.teamPoints,
        victoryPoints: dto.victoryPoints,
    };
}

/** Domain write input → wire payload; keeps the DTO out of the hooks. */
export function gameWriteInputToDto(input: GameWriteInput): GameRequestDto {
    return {
        gameNumber: input.gameNumber,
        beschiss: input.beschiss,
        version: input.version,
        teams: mapPair(input.teams, teamResultToDto),
    };
}

function teamResultToDto(team: GameWriteInput['teams'][number]): TeamResultRequestDto {
    return {
        teamId: team.teamId,
        baseScore: team.baseScore,
        players: mapPair(team.players, (p) => ({
            playerId: p.playerId,
            position: p.position,
            call: p.call,
            bombs: p.bombs,
        })),
    };
}
