import { createFileRoute, Link } from '@tanstack/react-router';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Landing state after a plain registration: the account exists but an owner must
 * approve it before it can sign in. Public, so it needs no session.
 */
function PendingApprovalPage() {
    const { t } = useI18n();

    return (
        <section className="flex max-w-md flex-col gap-4">
            <div className="flex flex-col gap-2">
                <h1 className="text-2xl font-semibold">{t('pendingApproval.title')}</h1>
                <p className="text-muted-foreground">{t('pendingApproval.description')}</p>
            </div>
            <Link to="/login" className="text-sm text-primary underline-offset-4 hover:underline">
                {t('pendingApproval.loginLink')}
            </Link>
        </section>
    );
}

export const Route = createFileRoute('/pending-approval')({
    component: PendingApprovalPage,
});
