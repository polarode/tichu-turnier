import { Link, Outlet } from '@tanstack/react-router';

import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole } from '@/vocabulary/admin.enum.ts';

/**
 * Frame for every guarded admin screen. Who is signed in and the logout action
 * live in the shell's avatar menu; platform owners additionally get the entry
 * point to the owner console here, other admins never see it.
 */
export function AdminLayout() {
    const { t } = useI18n();
    const { data: admin } = useSession();

    return (
        <div className="flex flex-col gap-6">
            {/* The route guard redirects signed-out visitors, so this is a safety net
                rather than the normal path: still show a way to sign in. */}
            {admin === null ? (
                <div className="flex justify-end text-sm">
                    <Link to="/login" className="text-primary underline-offset-4 hover:underline">
                        {t('nav.login')}
                    </Link>
                </div>
            ) : null}
            {admin?.role === AdminRole.OWNER ? (
                <div className="flex justify-end text-sm">
                    <Link to="/admin/console" className="text-primary underline-offset-4 hover:underline">
                        {t('ownerConsole.link')}
                    </Link>
                </div>
            ) : null}
            <Outlet />
        </div>
    );
}
