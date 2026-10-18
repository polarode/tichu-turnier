import { describe, expect, it } from 'vitest';

import type { PlayerDto, TeamDto } from '@/team-api/dtos/team.dto.ts';
import {
    playerDtoToDomain,
    teamDtoToDomain,
    teamToWriteInput,
    teamWriteInputToDto,
} from '@/team-api/mappers/team.mapper.ts';

const playerOne: PlayerDto = { id: 'p-1', teamId: 'team-1', name: 'Alice' };
const playerTwo: PlayerDto = { id: 'p-2', teamId: 'team-1', name: 'Bob' };
const team: TeamDto = {
    id: 'team-1',
    tournamentId: 't-1',
    name: 'The Aces',
    players: [playerOne, playerTwo],
};

describe('playerDtoToDomain', () => {
    it('maps a player DTO to the domain model', () => {
        expect(playerDtoToDomain(playerOne)).toEqual({
            id: 'p-1',
            teamId: 'team-1',
            name: 'Alice',
        });
    });
});

describe('teamDtoToDomain', () => {
    it('maps the team and both players', () => {
        const mapped = teamDtoToDomain(team);

        expect(mapped.id).toBe('team-1');
        expect(mapped.tournamentId).toBe('t-1');
        expect(mapped.name).toBe('The Aces');
        expect(mapped.players).toEqual([
            { id: 'p-1', teamId: 'team-1', name: 'Alice' },
            { id: 'p-2', teamId: 'team-1', name: 'Bob' },
        ]);
    });
});

describe('teamToWriteInput', () => {
    it('maps a domain team back to editable input', () => {
        expect(teamToWriteInput(teamDtoToDomain(team))).toEqual({
            name: 'The Aces',
            playerOneName: 'Alice',
            playerTwoName: 'Bob',
        });
    });
});

describe('teamWriteInputToDto', () => {
    it('maps domain write input to the wire payload', () => {
        expect(
            teamWriteInputToDto({
                name: 'The Aces',
                playerOneName: 'Alice',
                playerTwoName: 'Bob',
            }),
        ).toEqual({
            name: 'The Aces',
            playerOneName: 'Alice',
            playerTwoName: 'Bob',
        });
    });
});
