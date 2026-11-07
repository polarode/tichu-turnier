import { Link, useNavigate } from '@tanstack/react-router';
import { LogOut, ShieldCheck } from 'lucide-react';
import { useRef, useState } from 'react';

import { useLogoutAdmin, useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { useDropdownDismiss } from '@/shared/hooks/use-dropdown-dismiss.hook.ts';
import { AdminRole } from '@/vocabulary/admin.enum.ts';

const MENU_ITEM_CLASS =
    'flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors outline-none hover:bg-accent hover:text-accent-foreground focus-visible:bg-accent focus-visible:text-accent-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset disabled:opacity-50';

/**
 * Avatar in the top-right corner, shown only while a session exists. Clicking it
 * opens a menu with the signed-in email, the owner console entry (platform owners
 * only) and the logout action. It resolves the
 * session itself (sharing the admin guard's cache entry), so it also appears when
 * a signed-in admin opens a public or team page first; logout clears the entry
 * and the avatar disappears with it.
 */
export function UserMenu() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const logout = useLogoutAdmin();
    const { data: admin } = useSession();

    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    useDropdownDismiss(open, rootRef, () => setOpen(false));

    if (!admin) return null;

    const onLogout = async () => {
        setOpen(false);
        await logout.mutateAsync();
        await navigate({ to: '/public' });
    };

    return (
        <div ref={rootRef} className="relative">
            <button
                type="button"
                aria-label={t('auth.menu')}
                aria-haspopup="menu"
                aria-expanded={open}
                onClick={() => setOpen((wasOpen) => !wasOpen)}
                className="flex size-10 items-center justify-center rounded-full bg-primary text-primary-foreground transition-colors outline-none hover:bg-primary/90 focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-header"
            >
                <span aria-hidden="true" className="text-sm font-semibold uppercase">
                    {admin.email.charAt(0)}
                </span>
            </button>

            {open && (
                <div
                    role="menu"
                    aria-label={t('auth.menu')}
                    className="absolute right-0 z-10 mt-1 min-w-48 overflow-hidden rounded-md border border-border bg-popover text-popover-foreground shadow-md"
                >
                    <div className="border-b border-border px-3 py-2 text-sm">
                        <div className="text-xs text-muted-foreground">{t('auth.signedInAs')}</div>
                        <div className="truncate font-medium text-foreground">{admin.email}</div>
                    </div>
                    {admin.role === AdminRole.OWNER ? (
                        <Link
                            to="/admin/console"
                            role="menuitem"
                            onClick={() => setOpen(false)}
                            className={MENU_ITEM_CLASS}
                        >
                            <ShieldCheck aria-hidden="true" className="size-4" />
                            {t('ownerConsole.link')}
                        </Link>
                    ) : null}
                    <button
                        type="button"
                        role="menuitem"
                        onClick={onLogout}
                        disabled={logout.isPending}
                        className={MENU_ITEM_CLASS}
                    >
                        <LogOut aria-hidden="true" className="size-4" />
                        {logout.isPending ? t('auth.logout.pending') : t('auth.logout')}
                    </button>
                </div>
            )}
        </div>
    );
}
