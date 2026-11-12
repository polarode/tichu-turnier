import { Badge } from '@tichu/ui/badge.tsx';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import { AdminAccounts } from '@/features/owner-console/components/admin-accounts.component.tsx';
import { AllTournaments } from '@/features/owner-console/components/all-tournaments.component.tsx';
import { Invites } from '@/features/owner-console/components/invites.component.tsx';
import { OwnerConsoleForbidden } from '@/features/owner-console/components/owner-console-forbidden.component.tsx';
import { PendingRequests } from '@/features/owner-console/components/pending-requests.component.tsx';
import {
    useAdminAccounts,
    useAllTournaments,
    useInvites,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { ApiError } from '@/shared/api/api-error.ts';
import { fillPlaceholders } from '@/shared/i18n/fill-placeholders.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole, AdminStatus } from '@/vocabulary/admin.enum.ts';

/** A 403 means the caller is not (or is no longer) an owner; show the forbidden state. */
function isForbidden(error: unknown): boolean {
    return error instanceof ApiError && error.code === 'FORBIDDEN';
}

/** The console's tabs, kept in the URL by the route. */
export type OwnerConsoleTab = 'accounts' | 'invites' | 'tournaments';

export const OWNER_CONSOLE_TABS: readonly OwnerConsoleTab[] = [
    'accounts',
    'invites',
    'tournaments',
];

interface OwnerConsoleProps {
    tab: OwnerConsoleTab;
    onTabChange: (tab: OwnerConsoleTab) => void;
}

/**
 * The platform-owner console, one tab per job: accounts (pending requests, then the
 * existing admins), invites and every tournament. The accounts tab counts waiting
 * requests, so they are not missed from another tab. Access is decided in two layers:
 * the client hides it from non-owners (and never fires its owner-only requests for
 * them), while the backend refuses the data with a 403 regardless of what the client does.
 */
export function OwnerConsole({ tab, onTabChange }: OwnerConsoleProps) {
    const { t } = useI18n();
    const { data: session } = useSession();
    const isOwner = session?.role === AdminRole.OWNER;

    const accountsQuery = useAdminAccounts(isOwner);
    const invitesQuery = useInvites(isOwner);
    const tournamentsQuery = useAllTournaments(isOwner);

    if (!isOwner) return <OwnerConsoleForbidden />;
    if (
        isForbidden(accountsQuery.error) ||
        isForbidden(invitesQuery.error) ||
        isForbidden(tournamentsQuery.error)
    ) {
        return <OwnerConsoleForbidden />;
    }

    const accounts = accountsQuery.data ?? [];
    const pending = accounts.filter((account) => account.status === AdminStatus.PENDING);
    const managed = accounts.filter((account) => account.status !== AdminStatus.PENDING);

    const loading = <p className="text-muted-foreground">{t('common.loading')}</p>;
    const failed = (
        <p role="alert" className="text-sm text-destructive">
            {t('ownerConsole.error')}
        </p>
    );

    return (
        <section className="flex flex-col gap-6">
            <PageHeader titleKey="ownerConsole.title" descriptionKey="ownerConsole.description" />

            <div className="flex flex-col gap-6">
                <TabList
                    id="owner-console-section"
                    variant="underline"
                    label={t('ownerConsole.tabs.label')}
                    value={tab}
                    onValueChange={onTabChange}
                    items={[
                        {
                            value: 'accounts',
                            label: (
                                <>
                                    {t('ownerConsole.tabs.accounts')}
                                    {pending.length > 0 ? (
                                        <>
                                            <Badge aria-hidden="true">{pending.length}</Badge>
                                            <span className="sr-only">
                                                {`, ${fillPlaceholders(t('ownerConsole.tabs.pendingCount'), { count: String(pending.length) })}`}
                                            </span>
                                        </>
                                    ) : null}
                                </>
                            ),
                        },
                        { value: 'invites', label: t('ownerConsole.tabs.invites') },
                        { value: 'tournaments', label: t('ownerConsole.tabs.tournaments') },
                    ]}
                />
                <TabPanel id="owner-console-section" value={tab} className="flex flex-col gap-8">
                    {tab === 'invites' ? (
                        invitesQuery.isPending ? (
                            loading
                        ) : invitesQuery.isError ? (
                            failed
                        ) : (
                            <Invites invites={invitesQuery.data ?? []} />
                        )
                    ) : tab === 'tournaments' ? (
                        tournamentsQuery.isPending ? (
                            loading
                        ) : tournamentsQuery.isError ? (
                            failed
                        ) : (
                            <AllTournaments tournaments={tournamentsQuery.data ?? []} />
                        )
                    ) : accountsQuery.isPending ? (
                        loading
                    ) : accountsQuery.isError ? (
                        failed
                    ) : (
                        <>
                            <PendingRequests accounts={pending} />
                            <AdminAccounts accounts={managed} />
                        </>
                    )}
                </TabPanel>
            </div>
        </section>
    );
}
