import { describe, expect, it } from 'vitest';

import {
    adminAccountDtoToDomain,
    inviteDtoToDomain,
    issuedInviteDtoToDomain,
} from '@/admin-api/mappers/admin.mapper.ts';

describe('adminAccountDtoToDomain', () => {
    it('maps an account DTO to the domain model with real dates', () => {
        const account = adminAccountDtoToDomain({
            id: 'admin-1',
            email: 'organizer@example.com',
            status: 'PENDING',
            role: 'ADMIN',
            createdAt: '2026-01-01T00:00:00Z',
            approvedBy: null,
            approvedAt: null,
            invitedBy: null,
            invitedAt: null,
        });

        expect(account).toEqual({
            id: 'admin-1',
            email: 'organizer@example.com',
            status: 'PENDING',
            role: 'ADMIN',
            createdAt: new Date('2026-01-01T00:00:00Z'),
            approvedBy: null,
            approvedAt: null,
            invitedBy: null,
            invitedAt: null,
        });
    });

    it('maps the approval and invite audit timestamps when present', () => {
        const account = adminAccountDtoToDomain({
            id: 'admin-1',
            email: 'organizer@example.com',
            status: 'ACTIVE',
            role: 'ADMIN',
            createdAt: '2026-01-01T00:00:00Z',
            approvedBy: 'owner-1',
            approvedAt: '2026-01-02T00:00:00Z',
            invitedBy: 'owner-2',
            invitedAt: '2026-01-03T00:00:00Z',
        });

        expect(account.approvedAt).toEqual(new Date('2026-01-02T00:00:00Z'));
        expect(account.invitedAt).toEqual(new Date('2026-01-03T00:00:00Z'));
        expect(account.approvedBy).toBe('owner-1');
        expect(account.invitedBy).toBe('owner-2');
    });
});

describe('inviteDtoToDomain', () => {
    it('maps an invite DTO without any token material', () => {
        const invite = inviteDtoToDomain({
            id: 'invite-1',
            email: 'invitee@example.com',
            status: 'PENDING',
            createdBy: 'owner-1',
            createdAt: '2026-01-01T00:00:00Z',
            expiresAt: '2026-01-08T00:00:00Z',
            spentAt: null,
        });

        expect(invite).toEqual({
            id: 'invite-1',
            email: 'invitee@example.com',
            status: 'PENDING',
            createdBy: 'owner-1',
            createdAt: new Date('2026-01-01T00:00:00Z'),
            expiresAt: new Date('2026-01-08T00:00:00Z'),
            spentAt: null,
        });
        expect(invite).not.toHaveProperty('token');
    });
});

describe('issuedInviteDtoToDomain', () => {
    it('carries the one-time token and link onto the invite', () => {
        const issued = issuedInviteDtoToDomain({
            id: 'invite-1',
            email: 'invitee@example.com',
            status: 'PENDING',
            createdBy: 'owner-1',
            createdAt: '2026-01-01T00:00:00Z',
            expiresAt: '2026-01-08T00:00:00Z',
            spentAt: null,
            token: 'raw-token',
            link: 'http://localhost:3000/register?invite=raw-token',
        });

        expect(issued.token).toBe('raw-token');
        expect(issued.link).toBe('http://localhost:3000/register?invite=raw-token');
        expect(issued.id).toBe('invite-1');
    });
});
