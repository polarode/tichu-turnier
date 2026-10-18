import { describe, expect, it } from 'vitest';

import { adminDtoToDomain } from '@/auth-api/mappers/auth.mapper.ts';

describe('adminDtoToDomain', () => {
    it('maps an admin DTO to the domain model', () => {
        const admin = adminDtoToDomain({ id: 'admin-1', email: 'organizer@example.com' });

        expect(admin).toEqual({ id: 'admin-1', email: 'organizer@example.com' });
    });
});
