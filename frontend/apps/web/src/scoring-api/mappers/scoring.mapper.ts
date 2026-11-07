import type {
    GameOutcome,
    GameWriteInput,
    RecordedGame,
    RecordedPlayer,
    RecordedTeam,
    ScheduledGame,
    SeatedTeams,
    TableConfirmation,
    TeamTable,
    TeamTableTeam,
} from '@/domain/game.domain.ts';
import type {
    ConfirmRequestDto,
    GameRequestDto,
    RecordedGameDto,
    ScheduledGameDto,
    TableConfirmationDto,
    TeamResultRequestDto,
    TeamTableDto,
} from '@/scoring-api/dtos/scoring.dto.ts';

/** Pure mapping functions. Unit-tested in isolation (see `__tests__`). */

/** Maps a 2-tuple element-wise, preserving the fixed pair shape the domain uses. */
function mapPair<T, R>(pair: readonly [T, T], f: (item: T) => R): [R, R] {
    return [f(pair[0]), f(pair[1])];
}

/** Maps a table's seated teams element-wise, preserving the two-or-three seat shape. */
export function mapSeated<T, R>(seated: SeatedTeams<T>, f: (item: T) => R): SeatedTeams<R> {
    return seated.length === 3 ?
            [f(seated[0]), f(seated[1]), f(seated[2])]
        :   [f(seated[0]), f(seated[1])];
}

export function teamTableDtoToDomain(dto: TeamTableDto): TeamTable {
    return {
        tournamentId: dto.tournamentId,
        roundNumber: dto.roundNumber,
        tableNumber: dto.tableNumber,
        confirmation: tableConfirmationDtoToDomain(dto),
        grandTichuBonus: dto.grandTichuBonus,
        doubleWinBonus: dto.doubleWinBonus,
        teams: mapSeated(dto.teams, teamTableTeamDtoToDomain),
        trio: dto.trio,
        schedule: dto.schedule.map(scheduledGameDtoToDomain),
    };
}

export function tableConfirmationDtoToDomain(dto: TableConfirmationDto): TableConfirmation {
    return { status: dto.status, confirmedTeamIds: [...dto.confirmedTeamIds] };
}

/** The confirm request for the games the team is looking at: each game's number and version. */
export function confirmRequestOf(games: readonly RecordedGame[]): ConfirmRequestDto {
    return { games: games.map((game) => ({ gameNumber: game.gameNumber, version: game.version })) };
}

export function scheduledGameDtoToDomain(dto: ScheduledGameDto): ScheduledGame {
    return { gameNumber: dto.gameNumber, teamIds: [dto.teamIds[0], dto.teamIds[1]] };
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
