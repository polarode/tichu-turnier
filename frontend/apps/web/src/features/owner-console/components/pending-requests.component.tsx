import { Button } from '@tichu/ui/button.tsx';
import { useState } from 'react';

import type { AdminAccount } from '@/domain/admin-account.domain.ts';
import {
    useApproveAdmin,
    useRejectAdmin,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Pending registration requests with approve/reject actions. The account list is owned
 * by the console page; approving or rejecting invalidates it, so a handled row leaves
 * this list on the next render.
 */
export function PendingRequests({ accounts }: { accounts: AdminAccount[] }) {
    const { t } = useI18n();
    const approve = useApproveAdmin();
    const reject = useRejectAdmin();
    const [error, setError] = useState<string>();

    const busy = approve.isPending || reject.isPending;

    const run = async (action: 'approve' | 'reject', id: string) => {
        setError(undefined);
        try {
            if (action === 'approve') {
                await approve.mutateAsync(id);
            } else {
                await reject.mutateAsync(id);
            }
        } catch (failure) {
            setError(localizedErrorMessage(failure, t));
        }
    };

    return (
        <section className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
                <h2 className="text-xl font-semibold">{t('ownerConsole.pending.title')}</h2>
                <p className="text-sm text-muted-foreground">
                    {t('ownerConsole.pending.description')}
                </p>
            </div>

            <FormAlert message={error} />

            {accounts.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('ownerConsole.pending.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {accounts.map((account) => (
                        <li
                            key={account.id}
                            className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border p-3"
                        >
                            <span className="font-medium">{account.email}</span>
                            <div className="flex items-center gap-2">
                                <Button
                                    size="sm"
                                    disabled={busy}
                                    onClick={() => void run('approve', account.id)}
                                >
                                    {t('ownerConsole.pending.approve')}
                                </Button>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    disabled={busy}
                                    onClick={() => void run('reject', account.id)}
                                >
                                    {t('ownerConsole.pending.reject')}
                                </Button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
