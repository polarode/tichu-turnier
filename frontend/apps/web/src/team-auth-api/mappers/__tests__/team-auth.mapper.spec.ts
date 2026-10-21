import { describe, expect, it } from 'vitest';

import { teamOverviewDtoToDomain, teamSessionDtoToDomain } from '@/team-auth-api/mappers/team-auth.mapper.ts';

describe('teamSessionDtoToDomain', () => {
    it('maps the wire DTO to the domain session with a Date expiry', () => {
        const session = teamSessionDtoToDomain({
            teamId: 'team-1',
            tournamentId: 't-1',
            expiresAt: '2026-01-01T10:00:00Z',
        });

        expect(session).toEqual({
            teamId: 'team-1',
            tournamentId: 't-1',
            expiresAt: new Date('2026-01-01T10:00:00Z'),
        });
    });
});

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
