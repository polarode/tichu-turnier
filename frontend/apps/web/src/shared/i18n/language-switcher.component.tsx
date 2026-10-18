import { Button } from '@tichu/ui/button.tsx';

import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { LOCALES, type Locale } from '@/shared/i18n/locale.ts';

const LANGUAGE_LABEL_KEY: Record<Locale, TranslationKey> = {
    de: 'language.de',
    en: 'language.en',
};

/** Visible de/en switch. Updating it re-renders every translated string. */
export function LanguageSwitcher() {
    const { locale, setLocale, t } = useI18n();

    return (
        <div className="flex items-center gap-1" role="group" aria-label={t('language.label')}>
            {LOCALES.map((code) => {
                const active = code === locale;
                return (
                    <Button
                        key={code}
                        type="button"
                        size="sm"
                        variant={active ? 'secondary' : 'ghost'}
                        aria-pressed={active}
                        onClick={() => setLocale(code)}
                    >
                        {t(LANGUAGE_LABEL_KEY[code])}
                    </Button>
                );
            })}
        </div>
    );
}
