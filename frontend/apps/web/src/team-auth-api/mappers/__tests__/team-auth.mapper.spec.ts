import { describe, expect, it } from 'vitest';

import { teamSessionDtoToDomain } from '@/team-auth-api/mappers/team-auth.mapper.ts';

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
