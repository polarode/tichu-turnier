import { describe, expect, it } from 'vitest';

import { teamOverviewDtoToDomain } from '@/team-auth-api/mappers/team-auth.mapper.ts';

describe('teamOverviewDtoToDomain', () => {
    it('maps the wire DTO to the domain overview, dropping transport-only fields', () => {
        const overview = teamOverviewDtoToDomain({
            teamId: 'team-1',
            tournamentId: 't-1',
            tournamentName: 'Spring Classic',
            teamName: 'Team Alpha',
            players: [
                { id: 'p-1', teamId: 'team-1', name: 'Alice' },
                { id: 'p-2', teamId: 'team-1', name: 'Bob' },
            ],
        });

        expect(overview).toEqual({
            teamId: 'team-1',
            tournamentId: 't-1',
            tournamentName: 'Spring Classic',
            teamName: 'Team Alpha',
            players: [
                { id: 'p-1', name: 'Alice' },
                { id: 'p-2', name: 'Bob' },
            ],
        });
    });
});
