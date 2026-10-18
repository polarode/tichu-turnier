import { describe, expect, it } from 'vitest';

import {
    tournamentDtoToDomain,
    tournamentSettingsDtoToDomain,
    tournamentSettingsToDto,
} from '@/tournament-api/mappers/tournament.mapper.ts';
import type { TournamentDto } from '@/tournament-api/dtos/tournament.dto.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

const dto: TournamentDto = {
    id: 't-1',
    name: 'Spring Classic',
    description: 'annual invitational',
    maxTeams: 16,
    settings: { allowGrandTichu: false },
    status: TournamentStatus.SETUP,
    createdAt: '2026-01-01T12:00:00.000Z',
    updatedAt: '2026-01-02T08:30:00.000Z',
};

describe('tournamentDtoToDomain', () => {
    it('maps a DTO to a domain Tournament and parses timestamps into Date objects', () => {
        const tournament = tournamentDtoToDomain(dto);

        expect(tournament.id).toBe('t-1');
        expect(tournament.name).toBe('Spring Classic');
        expect(tournament.maxTeams).toBe(16);
        expect(tournament.status).toBe(TournamentStatus.SETUP);
        expect(tournament.settings).toEqual({ allowGrandTichu: false });
        expect(tournament.createdAt).toBeInstanceOf(Date);
        expect(tournament.createdAt.toISOString()).toBe('2026-01-01T12:00:00.000Z');
        expect(tournament.updatedAt.toISOString()).toBe('2026-01-02T08:30:00.000Z');
    });
});

describe('tournament settings mapping', () => {
    it('maps settings DTO → domain', () => {
        expect(tournamentSettingsDtoToDomain({ allowGrandTichu: true })).toEqual({
            allowGrandTichu: true,
        });
    });

    it('maps settings domain → DTO', () => {
        expect(tournamentSettingsToDto({ allowGrandTichu: false })).toEqual({
            allowGrandTichu: false,
        });
    });
});
