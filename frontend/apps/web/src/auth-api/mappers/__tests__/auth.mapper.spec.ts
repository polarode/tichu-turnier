import { describe, expect, it } from 'vitest';

import {
    adminCredentialsToDto,
    adminDtoToDomain,
    adminRegistrationToDto,
} from '@/auth-api/mappers/auth.mapper.ts';

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

describe('adminRegistrationToDto', () => {
    it('maps credentials without an invite token', () => {
        expect(
            adminRegistrationToDto({ email: 'organizer@example.com', password: 's3cret-pw' }),
        ).toEqual({ email: 'organizer@example.com', password: 's3cret-pw' });
    });

    it('carries the invite token through to the wire payload', () => {
        expect(
            adminRegistrationToDto({
                email: 'invitee@example.com',
                password: 's3cret-pw',
                inviteToken: 'raw-invite-token',
            }),
        ).toEqual({
            email: 'invitee@example.com',
            password: 's3cret-pw',
            inviteToken: 'raw-invite-token',
        });
    });
});
