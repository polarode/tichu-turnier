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
        };

        expect(standingsEntryDtoToDomain(dto)).toEqual({
            rank: 2,
            teamId: 'team-owls',
            teamName: 'Owls',
            gamesPlayed: 8,
            victoryPoints: 9,
            tichuPoints: 410,
        });
    });
});
