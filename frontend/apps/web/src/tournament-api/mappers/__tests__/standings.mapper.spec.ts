import { describe, expect, it } from 'vitest';

import type { StandingsEntryDto } from '@/tournament-api/dtos/standings.dto.ts';
import { standingsEntryDtoToDomain } from '@/tournament-api/mappers/standings.mapper.ts';

describe('standingsEntryDtoToDomain', () => {
    it('maps a standings line from the wire to the domain unchanged in meaning', () => {
        const dto: StandingsEntryDto = {
            rank: 2,
            teamId: 'team-owls',
            teamName: 'Owls',
            gamesPlayed: 8,
            victoryPoints: 9,
            tichuPoints: 410,
            withdrawalReason: null,
        };

        expect(standingsEntryDtoToDomain(dto)).toEqual({
            rank: 2,
            teamId: 'team-owls',
            teamName: 'Owls',
            gamesPlayed: 8,
            victoryPoints: 9,
            tichuPoints: 410,
            withdrawalReason: null,
        });
    });

    it('keeps the rank of a team that withdrew voluntarily, with its reason', () => {
        const mapped = standingsEntryDtoToDomain({
            rank: 3,
            teamId: 'team-foxes',
            teamName: 'Foxes',
            gamesPlayed: 4,
            victoryPoints: 3,
            tichuPoints: 180,
            withdrawalReason: 'VOLUNTARY',
        });

        expect(mapped.rank).toBe(3);
        expect(mapped.withdrawalReason).toBe('VOLUNTARY');
    });

    it('maps a disqualified team to a null rank and its reason', () => {
        const mapped = standingsEntryDtoToDomain({
            rank: null,
            teamId: 'team-wolves',
            teamName: 'Wolves',
            gamesPlayed: 2,
            victoryPoints: 0,
            tichuPoints: 60,
            withdrawalReason: 'DISQUALIFIED',
        });

        expect(mapped.rank).toBeNull();
        expect(mapped.withdrawalReason).toBe('DISQUALIFIED');
    });
});
