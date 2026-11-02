import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminRepository } from '@/admin-api/admin.repository.ts';
import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { AdminAccount, IssuedInvite } from '@/domain/admin-account.domain.ts';
import type { AdminRole, AdminStatus } from '@/vocabulary/admin.enum.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: {
        register: vi.fn(),
        login: vi.fn(),
        refresh: vi.fn(),
        logout: vi.fn(),
    },
}));

vi.mock('@/admin-api/admin.repository.ts', () => ({
    AdminRepository: {
        listAccounts: vi.fn(),
        approve: vi.fn(),
        reject: vi.fn(),
        disable: vi.fn(),
        reactivate: vi.fn(),
        listInvites: vi.fn(),
        createInvite: vi.fn(),
        revokeInvite: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const adminRepo = vi.mocked(AdminRepository);

const owner = { id: 'owner-1', email: 'owner@example.com', role: 'OWNER' as const };
const plainAdmin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

function account({
    id,
    email,
    status = 'PENDING',
    role = 'ADMIN',
}: {
    id: string;
    email: string;
    status?: AdminStatus;
    role?: AdminRole;
}): AdminAccount {
    return {
        id,
        email,
        status,
        role,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        approvedBy: null,
        approvedAt: null,
        invitedBy: null,
        invitedAt: null,
    };
}

describe('owner console', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        adminRepo.listInvites.mockResolvedValue([]);
        adminRepo.listAccounts.mockResolvedValue([]);
    });

    it('opens the console from the avatar menu for owners', async () => {
        auth.refresh.mockResolvedValue(owner);
        adminRepo.listAccounts.mockResolvedValue([]);
        const { router } = renderApp('/public');

        await userEvent.click(await screen.findByRole('button', { name: 'Konto' }));
        await userEvent.click(screen.getByRole('menuitem', { name: 'Owner-Konsole' }));

        await vi.waitFor(() => expect(router.state.location.pathname).toBe('/admin/console'));
        expect(screen.queryByRole('menu', { name: 'Konto' })).not.toBeInTheDocument();
        expect(await screen.findByRole('heading', { name: 'Owner-Konsole' })).toBeInTheDocument();
    });

    it('approves a pending request and moves the account into the admin list', async () => {
        auth.refresh.mockResolvedValue(owner);
        const pending = account({ id: 'pending-1', email: 'newbie@example.com', status: 'PENDING' });
        const active = account({ id: 'pending-1', email: 'newbie@example.com', status: 'ACTIVE' });
        adminRepo.listAccounts.mockResolvedValueOnce([pending]).mockResolvedValue([active]);
        adminRepo.approve.mockResolvedValue(active);

        renderApp('/admin/console');

        // Owners reach the console; the pending request is listed.
        expect(await screen.findByText('newbie@example.com')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Freigeben' }));

        expect(adminRepo.approve).toHaveBeenCalledWith('pending-1');
        expect(await screen.findByText('Keine offenen Anfragen.')).toBeInTheDocument();
        expect(screen.getByText('Aktiv')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Deaktivieren' })).toBeInTheDocument();
    });

    it('rejects a pending request', async () => {
        auth.refresh.mockResolvedValue(owner);
        const pending = account({ id: 'pending-1', email: 'newbie@example.com', status: 'PENDING' });
        adminRepo.listAccounts.mockResolvedValueOnce([pending]).mockResolvedValue([]);
        adminRepo.reject.mockResolvedValue(undefined);

        renderApp('/admin/console');

        await userEvent.click(await screen.findByRole('button', { name: 'Ablehnen' }));

        expect(adminRepo.reject).toHaveBeenCalledWith('pending-1');
        expect(await screen.findByText('Keine offenen Anfragen.')).toBeInTheDocument();
    });

    it('issues an invite and shows the copyable link exactly once', async () => {
        auth.refresh.mockResolvedValue(owner);
        const issued: IssuedInvite = {
            id: 'invite-1',
            label: 'Ada Lovelace',
            status: 'PENDING',
            createdBy: 'owner-1',
            createdAt: new Date('2026-01-01T00:00:00Z'),
            expiresAt: new Date('2026-01-08T00:00:00Z'),
            spentAt: null,
            token: 'raw-token',
            link: 'http://localhost:3000/register?invite=raw-token',
        };
        adminRepo.createInvite.mockResolvedValue(issued);

        renderApp('/admin/console');

        await userEvent.type(await screen.findByLabelText('Name'), 'Ada Lovelace');
        await userEvent.click(screen.getByRole('button', { name: 'Einladung erstellen' }));

        expect(adminRepo.createInvite).toHaveBeenCalledWith('Ada Lovelace');
        const linkInput = await screen.findByLabelText('Einladungslink');
        expect(linkInput).toHaveValue('http://localhost:3000/register?invite=raw-token');
        expect(screen.getByRole('button', { name: 'Link kopieren' })).toBeInTheDocument();
    });

    it('disables an active admin and reflects the new status', async () => {
        auth.refresh.mockResolvedValue(owner);
        const active = account({ id: 'admin-2', email: 'existing@example.com', status: 'ACTIVE' });
        const disabled = account({ id: 'admin-2', email: 'existing@example.com', status: 'DISABLED' });
        adminRepo.listAccounts.mockResolvedValueOnce([active]).mockResolvedValue([disabled]);
        adminRepo.disable.mockResolvedValue(disabled);

        renderApp('/admin/console');

        await userEvent.click(await screen.findByRole('button', { name: 'Deaktivieren' }));

        expect(adminRepo.disable).toHaveBeenCalledWith('admin-2');
        expect(await screen.findByRole('button', { name: 'Reaktivieren' })).toBeInTheDocument();
    });

    it('shows non-owners the localized forbidden state and never calls the owner API', async () => {
        auth.refresh.mockResolvedValue(plainAdmin);

        renderApp('/admin/console');

        expect(await screen.findByRole('heading', { name: 'Nicht verfügbar' })).toBeInTheDocument();
        // Only owners get the console entry in the avatar menu.
        await userEvent.click(screen.getByRole('button', { name: 'Konto' }));
        expect(screen.getByRole('menu', { name: 'Konto' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Owner-Konsole' })).not.toBeInTheDocument();
        expect(adminRepo.listAccounts).not.toHaveBeenCalled();
        expect(adminRepo.listInvites).not.toHaveBeenCalled();
    });
});
