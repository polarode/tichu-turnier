import { Badge } from '@tichu/ui/badge.tsx';
import { Button } from '@tichu/ui/button.tsx';

import type { AdminAccount } from '@/domain/admin-account.domain.ts';
import { ConsoleRow } from '@/features/owner-console/components/console-row.component.tsx';
import { ConsoleSection } from '@/features/owner-console/components/console-section.component.tsx';
import {
    useDisableAdmin,
    useReactivateAdmin,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { useConsoleAction } from '@/features/owner-console/hooks/use-console-action.hook.ts';
import {
    ADMIN_ROLE_LABEL_KEYS,
    ADMIN_STATUS_LABEL_KEYS,
} from '@/features/owner-console/labels.ts';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole, AdminStatus } from '@/vocabulary/admin.enum.ts';

/** The action an account row offers, derived from its status. Owners are never actionable. */
function actionFor(account: AdminAccount): 'disable' | 'reactivate' | null {
    if (account.role === AdminRole.OWNER) return null;
    return account.status === AdminStatus.ACTIVE ? 'disable' : 'reactivate';
}

const ACTION_LABEL_KEYS: Record<'disable' | 'reactivate', TranslationKey> = {
    disable: 'ownerConsole.admins.disable',
    reactivate: 'ownerConsole.admins.reactivate',
};

/**
 * Existing (non-pending) admins with disable/reactivate actions. The owner account
 * itself is rendered without an action because the backend refuses to disable an owner.
 */
export function AdminAccounts({ accounts }: { accounts: AdminAccount[] }) {
    const { t } = useI18n();
    const disable = useDisableAdmin();
    const reactivate = useReactivateAdmin();
    const { error, run } = useConsoleAction(t);

    const busy = disable.isPending || reactivate.isPending;
    const handlers = {
        disable: (id: string) => disable.mutateAsync(id),
        reactivate: (id: string) => reactivate.mutateAsync(id),
    } as const;

    return (
        <ConsoleSection
            title={t('ownerConsole.admins.title')}
            description={t('ownerConsole.admins.description')}
            error={error}
        >
            {accounts.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('ownerConsole.admins.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {accounts.map((account) => {
                        const action = actionFor(account);
                        return (
                            <ConsoleRow key={account.id}>
                                <div className="flex flex-wrap items-center gap-3">
                                    <span className="font-medium">{account.email}</span>
                                    <Badge variant="outline">
                                        {t(ADMIN_ROLE_LABEL_KEYS[account.role])}
                                    </Badge>
                                    <Badge
                                        variant={
                                            account.status === AdminStatus.ACTIVE
                                                ? 'success'
                                                : 'secondary'
                                        }
                                    >
                                        {t(ADMIN_STATUS_LABEL_KEYS[account.status])}
                                    </Badge>
                                </div>
                                {action ? (
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        disabled={busy}
                                        onClick={() => void run(() => handlers[action](account.id))}
                                    >
                                        {t(ACTION_LABEL_KEYS[action])}
                                    </Button>
                                ) : null}
                            </ConsoleRow>
                        );
                    })}
                </ul>
            )}
        </ConsoleSection>
    );
}
