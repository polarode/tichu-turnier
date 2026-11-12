import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminRepository } from '@/admin-api/admin.repository.ts';
import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { AdminAccount, IssuedInvite } from '@/domain/admin-account.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import type { AdminRole, AdminStatus } from '@/vocabulary/admin.enum.ts';
import { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';
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
        listAllTournaments: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const adminRepo = vi.mocked(AdminRepository);

const owner = { id: 'owner-1', email: 'owner@example.com', role: 'OWNER' as const };
const plainAdmin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

function tournament(
    id: string,
    name: string,
    leadOrganizerEmail: string,
    status: Tournament['status'] = 'SETUP',
): Tournament {
    return {
        id,
        name,
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 4 },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        organizerRole: OrganizerRole.LEAD,
        leadOrganizerEmail,
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

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
        adminRepo.listAllTournaments.mockResolvedValue([]);
    });

    it('lists every tournament under its lead organizer, lead organizers sorted by email', async () => {
        auth.refresh.mockResolvedValue(owner);
        adminRepo.listAllTournaments.mockResolvedValue([
            tournament('t-1', 'Spring Cup', 'b@example.com'),
            tournament('t-2', 'Autumn Cup', 'a@example.com', 'ACTIVE'),
            tournament('t-3', 'Winter Cup', 'a@example.com'),
        ]);

        renderApp('/owner?tab=tournaments');

        const section = (await screen.findByRole('heading', { name: 'Turniere' })).closest(
            'section',
        );
        expect(section).not.toBeNull();
        const leadOrganizers = within(section!)
            .getAllByRole('heading', { level: 3 })
            .map((heading) => heading.textContent);
        expect(leadOrganizers).toEqual(['a@example.com', 'b@example.com']);

        const [firstGroup, secondGroup] = within(section!).getAllByRole('list');
        expect(
            within(firstGroup!)
                .getAllByRole('link')
                .map((link) => link.textContent),
        ).toEqual(['Autumn Cup', 'Winter Cup']);
        expect(within(firstGroup!).getByRole('link', { name: 'Autumn Cup' })).toHaveAttribute(
            'href',
            '/admin/tournaments/t-2',
        );
        const autumnRow = screen.getByRole('link', { name: 'Autumn Cup' }).closest('li');
        expect(within(autumnRow!).getByText('Läuft')).toBeInTheDocument();

        expect(within(secondGroup!).getByRole('link', { name: 'Spring Cup' })).toHaveAttribute(
            'href',
            '/admin/tournaments/t-1',
        );
    });

    it('splits the console into tabs, accounts first, and keeps the open tab in the URL', async () => {
        auth.refresh.mockResolvedValue(owner);
        const { router } = renderApp('/owner');

        const accountsTab = await screen.findByRole('tab', { name: 'Konten' });
        expect(accountsTab).toHaveAttribute('aria-selected', 'true');
        expect(await screen.findByRole('heading', { name: 'Offene Anfragen' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Admins' })).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Einladungen' })).not.toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Turniere' })).not.toBeInTheDocument();

        await userEvent.click(screen.getByRole('tab', { name: 'Turniere' }));

        expect(await screen.findByRole('heading', { name: 'Turniere' })).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: 'Offene Anfragen' })).not.toBeInTheDocument();
        await vi.waitFor(() =>
            expect(router.state.location.search).toEqual({ tab: 'tournaments' }),
        );
    });

    it('counts pending requests on the accounts tab, even while another tab is open', async () => {
        auth.refresh.mockResolvedValue(owner);
        adminRepo.listAccounts.mockResolvedValue([
            account({ id: 'p-1', email: 'one@example.com', status: 'PENDING' }),
            account({ id: 'p-2', email: 'two@example.com', status: 'PENDING' }),
        ]);

        renderApp('/owner?tab=invites');

        expect(
            await screen.findByRole('tab', { name: /^Konten\s*,\s*2 offene Anfragen$/ }),
        ).toHaveAttribute('aria-selected', 'false');
    });

    it('opens the console from its own Owner tab in the main navigation', async () => {
        auth.refresh.mockResolvedValue(owner);
        const { router } = renderApp('/public');
        const nav = await screen.findByRole('navigation', { name: 'Hauptnavigation' });

        await userEvent.click(await within(nav).findByRole('link', { name: 'Owner' }));
        // The owner's area leads the navigation, right before admin.
        expect(
            within(nav)
                .getAllByRole('link')
                .map((link) => link.textContent),
        ).toEqual(['Owner', 'Verwaltung', 'Team', 'Öffentlich']);

        await vi.waitFor(() => expect(router.state.location.pathname).toBe('/owner'));
        expect(await screen.findByRole('heading', { name: 'Owner-Konsole' })).toBeInTheDocument();
        expect(within(nav).getByRole('link', { name: 'Owner' })).toHaveAttribute(
            'aria-current',
            'page',
        );
        expect(within(nav).getByRole('link', { name: 'Verwaltung' })).not.toHaveAttribute(
            'aria-current',
        );
        // The console no longer doubles as an avatar menu entry.
        await userEvent.click(screen.getByRole('button', { name: 'Konto' }));
        expect(screen.queryByRole('menuitem', { name: 'Owner-Konsole' })).not.toBeInTheDocument();
    });

    it('approves a pending request and moves the account into the admin list', async () => {
        auth.refresh.mockResolvedValue(owner);
        const pending = account({
            id: 'pending-1',
            email: 'newbie@example.com',
            status: 'PENDING',
        });
        const active = account({ id: 'pending-1', email: 'newbie@example.com', status: 'ACTIVE' });
        adminRepo.listAccounts.mockResolvedValueOnce([pending]).mockResolvedValue([active]);
        adminRepo.approve.mockResolvedValue(active);

        renderApp('/owner');

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
        const pending = account({
            id: 'pending-1',
            email: 'newbie@example.com',
            status: 'PENDING',
        });
        adminRepo.listAccounts.mockResolvedValueOnce([pending]).mockResolvedValue([]);
        adminRepo.reject.mockResolvedValue(undefined);

        renderApp('/owner');

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

        renderApp('/owner');

        await userEvent.click(await screen.findByRole('tab', { name: 'Einladungen' }));
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
        const disabled = account({
            id: 'admin-2',
            email: 'existing@example.com',
            status: 'DISABLED',
        });
        adminRepo.listAccounts.mockResolvedValueOnce([active]).mockResolvedValue([disabled]);
        adminRepo.disable.mockResolvedValue(disabled);

        renderApp('/owner');

        await userEvent.click(await screen.findByRole('button', { name: 'Deaktivieren' }));

        expect(adminRepo.disable).toHaveBeenCalledWith('admin-2');
        expect(await screen.findByRole('button', { name: 'Reaktivieren' })).toBeInTheDocument();
    });

    it('shows non-owners the localized forbidden state and never calls the owner API', async () => {
        auth.refresh.mockResolvedValue(plainAdmin);

        renderApp('/owner');

        expect(await screen.findByRole('heading', { name: 'Nicht verfügbar' })).toBeInTheDocument();
        // Only owners get the Owner tab in the main navigation; the admin tab leads back.
        const nav = await screen.findByRole('navigation', { name: 'Hauptnavigation' });
        expect(await within(nav).findByRole('link', { name: 'Verwaltung' })).toBeInTheDocument();
        expect(within(nav).queryByRole('link', { name: 'Owner' })).not.toBeInTheDocument();
        expect(adminRepo.listAccounts).not.toHaveBeenCalled();
        expect(adminRepo.listInvites).not.toHaveBeenCalled();
        expect(adminRepo.listAllTournaments).not.toHaveBeenCalled();
    });
});
