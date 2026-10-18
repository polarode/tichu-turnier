import { Link } from '@tanstack/react-router';
import type { ReactNode } from 'react';

import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { LanguageSwitcher } from '@/shared/i18n/language-switcher.component.tsx';

const NAV_ITEMS: ReadonlyArray<{ to: '/admin' | '/team' | '/public'; labelKey: TranslationKey }> = [
    { to: '/admin', labelKey: 'nav.admin' },
    { to: '/team', labelKey: 'nav.team' },
    { to: '/public', labelKey: 'nav.public' },
];

const NAV_LINK_CLASS =
    'rounded-md px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground data-[status=active]:bg-accent data-[status=active]:text-accent-foreground';

/** The persistent frame every screen renders into: header with navigation and language switch. */
export function AppShell({ children }: { children: ReactNode }) {
    const { t } = useI18n();

    return (
        <div className="flex min-h-screen flex-col">
            <header className="border-b border-border">
                <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-4 px-6 py-3">
                    <Link to="/" activeOptions={{ exact: true }} className="flex flex-col">
                        <span className="text-base font-semibold">{t('app.name')}</span>
                        <span className="text-xs text-muted-foreground">{t('app.tagline')}</span>
                    </Link>

                    <nav aria-label={t('nav.primary')} className="flex items-center gap-1">
                        {NAV_ITEMS.map((item) => (
                            <Link key={item.to} to={item.to} className={NAV_LINK_CLASS}>
                                {t(item.labelKey)}
                            </Link>
                        ))}
                    </nav>

                    <div className="ml-auto flex items-center gap-3">
                        <LanguageSwitcher />
                        <Link to="/login" className={NAV_LINK_CLASS}>
                            {t('nav.login')}
                        </Link>
                    </div>
                </div>
            </header>

            <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-8">{children}</main>
        </div>
    );
}
