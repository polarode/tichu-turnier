import { describe, expect, it } from 'vitest';

import { adminCredentialsToDto, adminDtoToDomain } from '@/auth-api/mappers/auth.mapper.ts';

describe('adminDtoToDomain', () => {
    it('maps an admin DTO to the domain model', () => {
        const admin = adminDtoToDomain({ id: 'admin-1', email: 'organizer@example.com' });

        expect(admin).toEqual({ id: 'admin-1', email: 'organizer@example.com' });
    });
});

describe('adminCredentialsToDto', () => {
    it('maps domain credentials to the wire payload', () => {
        expect(
            adminCredentialsToDto({ email: 'organizer@example.com', password: 's3cret-pw' }),
        ).toEqual({ email: 'organizer@example.com', password: 's3cret-pw' });
    });
});
