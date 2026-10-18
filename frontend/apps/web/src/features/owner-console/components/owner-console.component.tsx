import { AdminAccounts } from '@/features/owner-console/components/admin-accounts.component.tsx';
import { Invites } from '@/features/owner-console/components/invites.component.tsx';
import { OwnerConsoleForbidden } from '@/features/owner-console/components/owner-console-forbidden.component.tsx';
import { PendingRequests } from '@/features/owner-console/components/pending-requests.component.tsx';
import {
    useAdminAccounts,
    useInvites,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { ApiError } from '@/shared/api/api-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole, AdminStatus } from '@/vocabulary/admin.enum.ts';

/** A 403 means the caller is not (or is no longer) an owner; show the forbidden state. */
function isForbidden(error: unknown): boolean {
    return error instanceof ApiError && error.code === 'FORBIDDEN';
}

/**
 * The platform-owner console. Access is decided in two layers: the client hides it from
 * non-owners (and never fires its owner-only requests for them), while the backend
 * refuses the data with a 403 regardless of what the client does.
 */
export function OwnerConsole() {
    const { t } = useI18n();
    const { data: session } = useSession();
    const isOwner = session?.role === AdminRole.OWNER;

    const accountsQuery = useAdminAccounts(isOwner);
    const invitesQuery = useInvites(isOwner);

    if (!isOwner) return <OwnerConsoleForbidden />;
    if (isForbidden(accountsQuery.error) || isForbidden(invitesQuery.error)) {
        return <OwnerConsoleForbidden />;
    }

    const accounts = accountsQuery.data ?? [];
    const pending = accounts.filter((account) => account.status === AdminStatus.PENDING);
    const managed = accounts.filter((account) => account.status !== AdminStatus.PENDING);

    return (
        <section className="flex flex-col gap-8">
            <PageHeader titleKey="ownerConsole.title" descriptionKey="ownerConsole.description" />

            {accountsQuery.isPending ? (
                <p className="text-muted-foreground">{t('common.loading')}</p>
            ) : accountsQuery.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {t('ownerConsole.error')}
                </p>
            ) : (
                <>
                    <PendingRequests accounts={pending} />
                    <AdminAccounts accounts={managed} />
                </>
            )}

            {invitesQuery.isPending ? (
                <p className="text-muted-foreground">{t('common.loading')}</p>
            ) : invitesQuery.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {t('ownerConsole.error')}
                </p>
            ) : (
                <Invites invites={invitesQuery.data ?? []} />
            )}
        </section>
    );
}
