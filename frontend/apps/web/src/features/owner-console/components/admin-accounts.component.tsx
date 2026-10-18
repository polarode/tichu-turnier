import { Badge } from '@tichu/ui/badge.tsx';
import { Button } from '@tichu/ui/button.tsx';
import { useState } from 'react';

import type { AdminAccount } from '@/domain/admin-account.domain.ts';
import {
    useDisableAdmin,
    useReactivateAdmin,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import {
    ADMIN_ROLE_LABEL_KEYS,
    ADMIN_STATUS_LABEL_KEYS,
} from '@/features/owner-console/labels.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole, AdminStatus } from '@/vocabulary/admin.enum.ts';

/** The action an account row offers, derived from its status. Owners are never actionable. */
function actionFor(account: AdminAccount): 'disable' | 'reactivate' | null {
    if (account.role === AdminRole.OWNER) return null;
    return account.status === AdminStatus.ACTIVE ? 'disable' : 'reactivate';
}

/**
 * Existing (non-pending) admins with disable/reactivate actions. The owner account
 * itself is rendered without an action because the backend refuses to disable an owner.
 */
export function AdminAccounts({ accounts }: { accounts: AdminAccount[] }) {
    const { t } = useI18n();
    const disable = useDisableAdmin();
    const reactivate = useReactivateAdmin();
    const [error, setError] = useState<string>();

    const busy = disable.isPending || reactivate.isPending;

    const run = async (action: 'disable' | 'reactivate', id: string) => {
        setError(undefined);
        try {
            if (action === 'disable') {
                await disable.mutateAsync(id);
            } else {
                await reactivate.mutateAsync(id);
            }
        } catch (failure) {
            setError(localizedErrorMessage(failure, t));
        }
    };

    return (
        <section className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
                <h2 className="text-xl font-semibold">{t('ownerConsole.admins.title')}</h2>
                <p className="text-sm text-muted-foreground">
                    {t('ownerConsole.admins.description')}
                </p>
            </div>

            <FormAlert message={error} />

            {accounts.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('ownerConsole.admins.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {accounts.map((account) => {
                        const action = actionFor(account);
                        return (
                            <li
                                key={account.id}
                                className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border p-3"
                            >
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
                                        onClick={() => void run(action, account.id)}
                                    >
                                        {action === 'disable'
                                            ? t('ownerConsole.admins.disable')
                                            : t('ownerConsole.admins.reactivate')}
                                    </Button>
                                ) : null}
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
}
