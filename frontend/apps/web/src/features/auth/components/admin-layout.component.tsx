import { Link, Outlet } from '@tanstack/react-router';

import { LogoutButton } from '@/features/auth/components/logout-button.component.tsx';
import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole } from '@/vocabulary/admin.enum.ts';

/**
 * Frame for every guarded admin screen: the session was resolved by the route guard,
 * so here we only show who is signed in and offer logout. Platform owners additionally
 * get the entry point to the owner console; other admins never see it.
 */
export function AdminLayout() {
    const { t } = useI18n();
    const { data: admin } = useSession();

    return (
        <div className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center justify-end gap-3 text-sm text-muted-foreground">
                {admin?.role === AdminRole.OWNER ? (
                    <Link
                        to="/admin/console"
                        className="text-primary underline-offset-4 hover:underline"
                    >
                        {t('ownerConsole.link')}
                    </Link>
                ) : null}
                {admin ? (
                    <span>
                        {t('auth.signedInAs')}{' '}
                        <span className="font-medium text-foreground">{admin.email}</span>
                    </span>
                ) : null}
                <LogoutButton />
            </div>
            <Outlet />
        </div>
    );
}
