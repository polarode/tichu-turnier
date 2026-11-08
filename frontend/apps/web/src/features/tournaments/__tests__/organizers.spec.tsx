import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { Organizers } from '@/domain/organizers.domain.ts';
import type { Player } from '@/domain/player.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { statisticsQueryKey } from '@/shared/constants/query-keys.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';
import type { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: { register: vi.fn(), login: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}));
vi.mock('@/tournament-api/tournament.repository.ts', async () => {
    const { mockTournamentRepositoryModule } =
        await import('@/test-helpers/tournament-repository.mock.ts');
    return mockTournamentRepositoryModule();
});
vi.mock('@/team-api/team.repository.ts', () => ({
    TeamRepository: {
        list: vi.fn(),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const teams = vi.mocked(TeamRepository);

const admin = { id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' as const };

function tournament(
    status: TournamentStatus,
    organizerRole: Tournament['organizerRole'] = OrganizerRole.LEAD,
): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 100, doubleWinBonus: 200, totalRounds: 4 },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        organizerRole,
        leadOrganizerEmail: 'lead@example.com',
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(id: string, name: string): Team {
    const players: readonly [Player, Player] = [
        { id: `${id}-p1`, teamId: id, name: `${name} 1` },
        { id: `${id}-p2`, teamId: id, name: `${name} 2` },
    ];
    return { id, tournamentId: 't-1', name, players };
}

function organizers(...coEmails: string[]): Organizers {
    return {
        lead: { email: 'lead@example.com', disabled: false },
        coOrganizers: coEmails.map((email) => ({
            adminUserId: `admin-${email}`,
            email,
            addedAt: new Date('2026-02-01T00:00:00Z'),
            disabled: false,
        })),
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue(admin);
    teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
    tournaments.rounds.mockResolvedValue([]);
});

describe('organizers tab', () => {
    it('shows Teams and Organizers tabs in setup and lists the organizers once opened', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        tournaments.listOrganizers.mockResolvedValue(organizers('co@example.com'));
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        expect(within(sections).getByRole('tab', { name: 'Teams (2)' })).toBeInTheDocument();

        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));

        expect(await screen.findByText('lead@example.com')).toBeInTheDocument();
        expect(screen.getByText('Leitung')).toBeInTheDocument();
        expect(screen.getByText('co@example.com')).toBeInTheDocument();
        expect(screen.getByText('Co-Organisator')).toBeInTheDocument();
    });

    it.each(['ACTIVE', 'PUBLISHED'] as const)(
        'puts the Organizers tab last once the tournament is %s',
        async (status) => {
            tournaments.get.mockResolvedValue(tournament(status));
            tournaments.listOrganizers.mockResolvedValue(organizers());
            renderApp('/admin/tournaments/t-1');

            const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
            const names = within(sections)
                .getAllByRole('tab')
                .map((tab) => tab.textContent);
            expect(names.at(-1)).toBe('Organisatoren');
        },
    );
});

describe('organizers as lead organizer', () => {
    it('adds a co-organizer by email, clears the field and lists them', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers.mockResolvedValue(organizers('co@example.com'));
        tournaments.addCoOrganizer.mockResolvedValue(
            organizers('co@example.com', 'new@example.com'),
        );
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));
        const input = await screen.findByLabelText('E-Mail des Admins');
        await userEvent.type(input, 'new@example.com');
        await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

        await waitFor(() =>
            expect(tournaments.addCoOrganizer).toHaveBeenCalledWith('t-1', 'new@example.com'),
        );
        expect(await screen.findByText('new@example.com')).toBeInTheDocument();
        expect(screen.getByLabelText('E-Mail des Admins')).toHaveValue('');
    });

    it('asks for an email before submitting an empty field', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers.mockResolvedValue(organizers());
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Hinzufügen' }));

        expect(await screen.findByText('E-Mail ist erforderlich.')).toBeInTheDocument();
        expect(tournaments.addCoOrganizer).not.toHaveBeenCalled();
    });

    it('shows the localized server error under the email field', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers.mockResolvedValue(organizers());
        tournaments.addCoOrganizer.mockRejectedValue(
            new ApiError({
                code: 'NO_ACTIVE_ADMIN_WITH_EMAIL',
                status: 404,
                field: 'email',
                message: 'no admin',
            }),
        );
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));
        await userEvent.type(
            await screen.findByLabelText('E-Mail des Admins'),
            'nobody@example.com',
        );
        await userEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

        const message = await screen.findByText(
            'Kein aktives Admin-Konto mit dieser E-Mail-Adresse.',
        );
        expect(message).toBeInTheDocument();
        expect(screen.getByLabelText('E-Mail des Admins')).toHaveAttribute('aria-invalid', 'true');
    });

    it('removes a co-organizer after confirmation and drops the row after the refetch', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers
            .mockResolvedValueOnce(organizers('co@example.com'))
            .mockResolvedValueOnce(organizers());
        tournaments.removeCoOrganizer.mockResolvedValue(undefined);
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));
        await userEvent.click(
            await screen.findByRole('button', { name: 'Entfernen – co@example.com' }),
        );
        const confirmDialog = await screen.findByRole('dialog', {
            name: 'Co-Organisator entfernen?',
        });
        await userEvent.click(within(confirmDialog).getByRole('button', { name: 'Entfernen' }));

        await waitFor(() =>
            expect(tournaments.removeCoOrganizer).toHaveBeenCalledWith(
                't-1',
                'admin-co@example.com',
            ),
        );
        await waitFor(() => expect(screen.queryByText('co@example.com')).not.toBeInTheDocument());
    });

    it('gives each co-organizer a remove button addressable by their email', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers.mockResolvedValue(
            organizers('co@example.com', 'other@example.com'),
        );
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));

        expect(
            await screen.findByRole('button', { name: 'Entfernen – co@example.com' }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole('button', { name: 'Entfernen – other@example.com' }),
        ).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Entfernen' })).not.toBeInTheDocument();
    });

    it('offers no leave button to the lead organizer', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers.mockResolvedValue(organizers('co@example.com'));
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));

        expect(await screen.findByText('co@example.com')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Verlassen' })).not.toBeInTheDocument();
    });
});

describe('organizers as co-organizer', () => {
    it('lists the organizers without an add form', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.CO));
        tournaments.listOrganizers.mockResolvedValue(organizers('co@example.com'));
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));

        expect(await screen.findByText('co@example.com')).toBeInTheDocument();
        expect(screen.queryByLabelText('E-Mail des Admins')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Hinzufügen' })).not.toBeInTheDocument();
    });

    it('shows Leave only on the own row and no remove buttons', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.CO));
        tournaments.listOrganizers.mockResolvedValue({
            lead: { email: 'lead@example.com', disabled: false },
            coOrganizers: [
                {
                    adminUserId: 'admin-1',
                    email: 'admin@example.com',
                    addedAt: new Date('2026-02-01T00:00:00Z'),
                    disabled: false,
                },
                {
                    adminUserId: 'admin-2',
                    email: 'other@example.com',
                    addedAt: new Date('2026-02-02T00:00:00Z'),
                    disabled: false,
                },
            ],
        });
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));

        const ownRow = (await screen.findByText('admin@example.com')).closest('li');
        const otherRow = screen.getByText('other@example.com').closest('li');
        expect(ownRow).not.toBeNull();
        expect(otherRow).not.toBeNull();
        expect(within(ownRow!).getByRole('button', { name: 'Verlassen' })).toBeInTheDocument();
        expect(
            within(otherRow!).queryByRole('button', { name: 'Verlassen' }),
        ).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Entfernen' })).not.toBeInTheDocument();
    });

    it('lets the co-organizer leave, drops the tournament from the cache and lands on the dashboard with the notice', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.CO));
        tournaments.list.mockResolvedValue([tournament('SETUP', OrganizerRole.CO)]);
        tournaments.listOrganizers.mockResolvedValue({
            lead: { email: 'lead@example.com', disabled: false },
            coOrganizers: [
                {
                    adminUserId: 'admin-1',
                    email: 'admin@example.com',
                    addedAt: new Date('2026-02-01T00:00:00Z'),
                    disabled: false,
                },
            ],
        });
        tournaments.removeCoOrganizer.mockResolvedValue(undefined);
        const { queryClient } = renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));
        queryClient.setQueryData(statisticsQueryKey('t-1'), {
            teams: [],
            highlights: [],
            trends: [],
        });
        const ownRow = (await screen.findByText('admin@example.com')).closest('li');
        await userEvent.click(within(ownRow!).getByRole('button', { name: 'Verlassen' }));
        const confirmDialog = await screen.findByRole('dialog', { name: 'Turnier verlassen?' });
        await userEvent.click(within(confirmDialog).getByRole('button', { name: 'Verlassen' }));

        await waitFor(() =>
            expect(tournaments.removeCoOrganizer).toHaveBeenCalledWith('t-1', 'admin-1'),
        );
        expect(await screen.findByRole('status')).toHaveTextContent(
            'Du hast Jahresturnier verlassen.',
        );
        expect(tournaments.get).toHaveBeenCalledTimes(1);
        expect(queryClient.getQueryData(statisticsQueryKey('t-1'))).toBeUndefined();
    });
});

describe('organizers disabled marking', () => {
    it('marks a disabled lead and a disabled co-organizer with a Disabled badge', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', OrganizerRole.LEAD));
        tournaments.listOrganizers.mockResolvedValue({
            lead: { email: 'lead@example.com', disabled: true },
            coOrganizers: [
                {
                    adminUserId: 'admin-co',
                    email: 'co@example.com',
                    addedAt: new Date('2026-02-01T00:00:00Z'),
                    disabled: true,
                },
            ],
        });
        renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await userEvent.click(within(sections).getByRole('tab', { name: 'Organisatoren' }));

        const leadRow = (await screen.findByText('lead@example.com')).closest('li');
        const coRow = screen.getByText('co@example.com').closest('li');
        expect(within(leadRow!).getByText('Deaktiviert')).toBeInTheDocument();
        expect(within(coRow!).getByText('Deaktiviert')).toBeInTheDocument();
    });
});
