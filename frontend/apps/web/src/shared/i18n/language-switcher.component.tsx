import { useRef, useState } from 'react';

import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { LOCALES, type Locale } from '@/shared/i18n/locale.ts';
import { useDropdownDismiss } from '@/shared/hooks/use-dropdown-dismiss.hook.ts';

const LANGUAGE_LABEL_KEY: Record<Locale, TranslationKey> = {
    de: 'language.de',
    en: 'language.en',
};

/** Flag per locale (emoji), shown as the switcher trigger. */
const LANGUAGE_FLAG: Record<Locale, string> = {
    de: '🇩🇪',
    en: '🇬🇧',
};

/** Compact flag in the top-right corner. Clicking it opens a dropdown to change the language. */
export function LanguageSwitcher() {
    const { locale, setLocale, t } = useI18n();
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    useDropdownDismiss(open, rootRef, () => setOpen(false));

    return (
        <div ref={rootRef} className="relative">
            <button
                type="button"
                aria-label={t('language.label')}
                aria-haspopup="menu"
                aria-expanded={open}
                aria-description={t(LANGUAGE_LABEL_KEY[locale])}
                onClick={() => setOpen((wasOpen) => !wasOpen)}
                className="rounded-md p-1.5 text-lg leading-none transition-colors hover:bg-accent"
            >
                {LANGUAGE_FLAG[locale]}
            </button>

            {open && (
                <div
                    role="menu"
                    aria-label={t('language.label')}
                    className="absolute right-0 z-10 mt-1 min-w-36 overflow-hidden rounded-md border border-border bg-popover shadow-md"
                >
                    {LOCALES.map((code) => {
                        const active = code === locale;
                        return (
                            <button
                                key={code}
                                type="button"
                                role="menuitemradio"
                                aria-checked={active}
                                onClick={() => {
                                    setLocale(code);
                                    setOpen(false);
                                }}
                                className="flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors hover:bg-accent hover:text-accent-foreground"
                            >
                                <span aria-hidden="true">{LANGUAGE_FLAG[code]}</span>
                                <span>{t(LANGUAGE_LABEL_KEY[code])}</span>
                                {active && <span aria-hidden="true" className="ml-auto">✓</span>}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
