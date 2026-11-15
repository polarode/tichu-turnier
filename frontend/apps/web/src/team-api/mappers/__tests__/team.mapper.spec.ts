import { describe, expect, it } from 'vitest';

import type { PlayerDto, TeamDto } from '@/team-api/dtos/team.dto.ts';
import {
    playerDtoToDomain,
    teamDtoToDomain,
    teamToWriteInput,
    teamWriteInputToDto,
    withdrawalInputToDto,
} from '@/team-api/mappers/team.mapper.ts';

const playerOne: PlayerDto = { id: 'p-1', teamId: 'team-1', name: 'Alice' };
const playerTwo: PlayerDto = { id: 'p-2', teamId: 'team-1', name: 'Bob' };
const team: TeamDto = {
    id: 'team-1',
    tournamentId: 't-1',
    name: 'The Aces',
    players: [playerOne, playerTwo],
    withdrawal: null,
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

    it('maps a team that plays on to a null withdrawal', () => {
        expect(teamDtoToDomain(team).withdrawal).toBeNull();
    });

    it('maps a withdrawn team to its reason, compensation, round and instant', () => {
        const mapped = teamDtoToDomain({
            ...team,
            withdrawal: {
                reason: 'VOLUNTARY',
                compensation: 'HALF',
                roundNumber: 2,
                withdrawnAt: '2026-11-14T18:30:00Z',
            },
        });

        expect(mapped.withdrawal).toEqual({
            reason: 'VOLUNTARY',
            compensation: 'HALF',
            roundNumber: 2,
            withdrawnAt: new Date('2026-11-14T18:30:00Z'),
        });
    });
});

describe('withdrawalInputToDto', () => {
    it('maps the withdrawal input to the wire payload', () => {
        expect(withdrawalInputToDto({ reason: 'DISQUALIFIED', compensation: 'FULL' })).toEqual({
            reason: 'DISQUALIFIED',
            compensation: 'FULL',
        });
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
