import { Link } from '@tanstack/react-router';
import { ClipboardList, type LucideIcon, ShieldCheck, Trophy, Users } from 'lucide-react';
import { cn } from '@tichu/ui/lib/utils.ts';
import type { ReactNode } from 'react';

import { SignInPopover } from '@/features/auth/components/sign-in-popover.component.tsx';
import { UserMenu } from '@/features/auth/components/user-menu.component.tsx';
import { useSession } from '@/features/auth/hooks/auth-queries.hook.ts';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { AdminRole } from '@/vocabulary/admin.enum.ts';
import { LanguageSwitcher } from '@/shared/i18n/language-switcher.component.tsx';
import { ThemeSwitcher } from '@/shared/theme/theme-switcher.component.tsx';

/** The admin area is offered only to a confirmed session; visitors reach sign-in from the corner. */
const ADMIN_AREA = '/admin';
/** The owner area is offered only to the platform owner. */
const OWNER_AREA = '/owner';

/**
 * Each area carries an icon and one of the suits from the header stripe; the
 * active area is underlined in its suit colour. The bar classes are spelled out
 * in full so Tailwind generates them.
 */
const NAV_ITEMS: ReadonlyArray<{
    to: '/admin' | '/team' | '/public' | '/owner';
    labelKey: TranslationKey;
    icon: LucideIcon;
    activeBar: string;
}> = [
    // The owner's own area leads, right before admin.
    {
        to: OWNER_AREA,
        labelKey: 'nav.owner',
        icon: ShieldCheck,
        activeBar: 'data-[status=active]:after:bg-suit-sword',
    },
    {
        to: ADMIN_AREA,
        labelKey: 'nav.admin',
        icon: ClipboardList,
        activeBar: 'data-[status=active]:after:bg-suit-jade',
    },
    {
        to: '/team',
        labelKey: 'nav.team',
        icon: Users,
        activeBar: 'data-[status=active]:after:bg-suit-pagoda',
    },
    {
        to: '/public',
        labelKey: 'nav.public',
        icon: Trophy,
        activeBar: 'data-[status=active]:after:bg-suit-star',
    },
];

const NAV_LINK_CLASS =
    "relative inline-flex min-h-11 items-center justify-center font-medium text-header-muted transition-colors outline-none after:absolute after:inset-x-2 after:bottom-0 after:h-[3px] after:rounded-full after:content-[''] hover:text-header-foreground focus-visible:rounded-md focus-visible:ring-2 focus-visible:ring-header-foreground data-[status=active]:text-header-foreground";

/**
 * The persistent frame every screen renders into: header with navigation, theme and language switch.
 * On phones the navigation drops to its own full-width row below the brand and controls, as
 * equal-width tabs. The admin tab appears only once a session is confirmed and the owner tab only
 * for the platform owner, so nobody sees an area they cannot enter. Four tabs no longer fit a phone
 * row with icon and label side by side, so the owner's phone bar stacks the icon above a smaller label.
 */
export function AppShell({ children }: { children: ReactNode }) {
    const { t } = useI18n();
    const { data: admin } = useSession();
    const navItems = NAV_ITEMS.filter((item) =>
        item.to === ADMIN_AREA
            ? Boolean(admin)
            : item.to === OWNER_AREA
              ? admin?.role === AdminRole.OWNER
              : true,
    );
    const compact = navItems.length > 3;

    return (
        <div className="flex min-h-screen flex-col">
            <header className="border-b border-border bg-header text-header-foreground">
                {/* The four Tichu suits — Jade, Pagoda, Star, Sword — as a thin stripe. */}
                <div aria-hidden="true" className="grid h-1 grid-cols-4">
                    <span className="bg-suit-jade" />
                    <span className="bg-suit-pagoda" />
                    <span className="bg-suit-star" />
                    <span className="bg-suit-sword" />
                </div>
                <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
                    <Link
                        to="/"
                        activeOptions={{ exact: true }}
                        className="flex flex-col rounded-md outline-none focus-visible:ring-2 focus-visible:ring-header-foreground"
                    >
                        <span className="text-base font-semibold">{t('app.name')}</span>
                        <span className="hidden text-xs text-header-muted sm:block">
                            {t('app.tagline')}
                        </span>
                    </Link>

                    <nav
                        aria-label={t('nav.primary')}
                        className="order-last grid w-full auto-cols-fr grid-flow-col sm:order-none sm:flex sm:w-auto sm:gap-2"
                    >
                        {navItems.map(({ to, labelKey, icon: Icon, activeBar }) => (
                            <Link
                                key={to}
                                to={to}
                                className={cn(
                                    NAV_LINK_CLASS,
                                    compact
                                        ? 'flex-col gap-0.5 px-1 text-xs sm:flex-row sm:gap-2 sm:px-3 sm:text-sm'
                                        : 'gap-2 px-3 text-sm',
                                    activeBar,
                                )}
                            >
                                <Icon aria-hidden="true" className="size-4" />
                                {t(labelKey)}
                            </Link>
                        ))}
                    </nav>

                    <div className="ml-auto flex items-center gap-2 sm:gap-3">
                        <ThemeSwitcher />
                        <LanguageSwitcher />
                        <UserMenu />
                        <SignInPopover />
                    </div>
                </div>
            </header>

            <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
                {children}
            </main>
        </div>
    );
}
