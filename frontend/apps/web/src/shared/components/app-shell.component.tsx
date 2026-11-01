import { Link } from '@tanstack/react-router';
import type { ReactNode } from 'react';

import { UserMenu } from '@/features/auth/components/user-menu.component.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { LanguageSwitcher } from '@/shared/i18n/language-switcher.component.tsx';
import { ThemeSwitcher } from '@/shared/theme/theme-switcher.component.tsx';

const NAV_ITEMS: ReadonlyArray<{ to: '/admin' | '/team' | '/public'; labelKey: TranslationKey }> = [
    { to: '/admin', labelKey: 'nav.admin' },
    { to: '/team', labelKey: 'nav.team' },
    { to: '/public', labelKey: 'nav.public' },
];

const NAV_LINK_CLASS =
    'rounded-md px-3 py-2 text-sm font-medium text-header-muted transition-colors outline-none hover:bg-header-active hover:text-header-foreground focus-visible:ring-2 focus-visible:ring-header-foreground sm:py-1.5 data-[status=active]:bg-header-active data-[status=active]:text-header-foreground';

/**
 * The persistent frame every screen renders into: header with navigation, theme and language switch.
 * On phones the navigation drops to its own full-width row below the brand and controls.
 */
export function AppShell({ children }: { children: ReactNode }) {
    const { t } = useI18n();

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
                        <span className="hidden text-xs text-header-muted sm:block">{t('app.tagline')}</span>
                    </Link>

                    <nav aria-label={t('nav.primary')} className="order-last flex w-full items-center gap-1 sm:order-none sm:w-auto">
                        {NAV_ITEMS.map((item) => (
                            <Link key={item.to} to={item.to} className={NAV_LINK_CLASS}>
                                {t(item.labelKey)}
                            </Link>
                        ))}
                    </nav>

                    <div className="ml-auto flex items-center gap-2 sm:gap-3">
                        <ThemeSwitcher />
                        <LanguageSwitcher />
                        <UserMenu />
                    </div>
                </div>
            </header>

            <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6 sm:py-8">{children}</main>
        </div>
    );
}
