import { Button } from '@tichu/ui/button.tsx';

import type { AdminAccount } from '@/domain/admin-account.domain.ts';
import { ConsoleRow } from '@/features/owner-console/components/console-row.component.tsx';
import { ConsoleSection } from '@/features/owner-console/components/console-section.component.tsx';
import {
    useApproveAdmin,
    useRejectAdmin,
} from '@/features/owner-console/hooks/owner-console-queries.hook.ts';
import { useConsoleAction } from '@/features/owner-console/hooks/use-console-action.hook.ts';
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
    const { error, run } = useConsoleAction(t);

    const busy = approve.isPending || reject.isPending;
    const handlers = {
        approve: (id: string) => approve.mutateAsync(id),
        reject: (id: string) => reject.mutateAsync(id),
    } as const;

    return (
        <ConsoleSection
            title={t('ownerConsole.pending.title')}
            description={t('ownerConsole.pending.description')}
            error={error}
        >
            {accounts.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('ownerConsole.pending.empty')}</p>
            ) : (
                <ul className="flex flex-col gap-2">
                    {accounts.map((account) => (
                        <ConsoleRow key={account.id}>
                            <span className="font-medium">{account.email}</span>
                            <div className="flex items-center gap-2">
                                <Button
                                    size="sm"
                                    disabled={busy}
                                    onClick={() => void run(() => handlers.approve(account.id))}
                                >
                                    {t('ownerConsole.pending.approve')}
                                </Button>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    disabled={busy}
                                    onClick={() => void run(() => handlers.reject(account.id))}
                                >
                                    {t('ownerConsole.pending.reject')}
                                </Button>
                            </div>
                        </ConsoleRow>
                    ))}
                </ul>
            )}
        </ConsoleSection>
    );
}
