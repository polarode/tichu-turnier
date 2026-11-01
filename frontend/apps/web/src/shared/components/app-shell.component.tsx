import { Link } from '@tanstack/react-router';
import type { ReactNode } from 'react';

import { UserMenu } from '@/features/auth/components/user-menu.component.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { LanguageSwitcher } from '@/shared/i18n/language-switcher.component.tsx';

const NAV_ITEMS: ReadonlyArray<{ to: '/admin' | '/team' | '/public'; labelKey: TranslationKey }> = [
    { to: '/admin', labelKey: 'nav.admin' },
    { to: '/team', labelKey: 'nav.team' },
    { to: '/public', labelKey: 'nav.public' },
];

const NAV_LINK_CLASS =
    'rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground sm:py-1.5 data-[status=active]:bg-accent data-[status=active]:text-accent-foreground';

/**
 * The persistent frame every screen renders into: header with navigation and language switch.
 * On phones the navigation drops to its own full-width row below the brand and controls.
 */
export function AppShell({ children }: { children: ReactNode }) {
    const { t } = useI18n();

    return (
        <div className="flex min-h-screen flex-col">
            <header className="border-b border-border">
                <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
                    <Link to="/" activeOptions={{ exact: true }} className="flex flex-col">
                        <span className="text-base font-semibold">{t('app.name')}</span>
                        <span className="hidden text-xs text-muted-foreground sm:block">{t('app.tagline')}</span>
                    </Link>

                    <nav aria-label={t('nav.primary')} className="order-last flex w-full items-center gap-1 sm:order-none sm:w-auto">
                        {NAV_ITEMS.map((item) => (
                            <Link key={item.to} to={item.to} className={NAV_LINK_CLASS}>
                                {t(item.labelKey)}
                            </Link>
                        ))}
                    </nav>

                    <div className="ml-auto flex items-center gap-2 sm:gap-3">
                        <LanguageSwitcher />
                        <UserMenu />
                    </div>
                </div>
            </header>

            <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6 sm:py-8">{children}</main>
        </div>
    );
}
