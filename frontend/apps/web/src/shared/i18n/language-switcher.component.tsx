import { HeaderMenu } from '@/shared/components/header-menu.component.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { LOCALES, type Locale } from '@/shared/i18n/locale.ts';

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

    return (
        <HeaderMenu
            label={t('language.label')}
            value={locale}
            onValueChange={setLocale}
            trigger={LANGUAGE_FLAG[locale]}
            options={LOCALES.map((code) => ({
                value: code,
                label: t(LANGUAGE_LABEL_KEY[code]),
                icon: LANGUAGE_FLAG[code],
            }))}
        />
    );
}
