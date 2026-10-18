import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { CATALOGS } from '@/shared/i18n/catalog.ts';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import {
    DEFAULT_LOCALE,
    type Locale,
    readStoredLocale,
    storeLocale,
} from '@/shared/i18n/locale.ts';

export interface I18n {
    locale: Locale;
    setLocale: (locale: Locale) => void;
    t: (key: TranslationKey) => string;
}

const I18nContext = createContext<I18n | null>(null);

interface I18nProviderProps {
    children: ReactNode;
    /**
     * Pins the initial language. Tests pass this for deterministic output;
     * production leaves it out so the stored preference (or the default) wins.
     */
    initialLocale?: Locale;
}

export function I18nProvider({ children, initialLocale }: I18nProviderProps) {
    const [locale, setLocaleState] = useState<Locale>(
        () => initialLocale ?? readStoredLocale() ?? DEFAULT_LOCALE,
    );

    const setLocale = useCallback((next: Locale) => {
        setLocaleState(next);
        storeLocale(next);
    }, []);

    const t = useCallback((key: TranslationKey) => CATALOGS[locale][key], [locale]);

    useEffect(() => {
        document.documentElement.lang = locale;
        document.title = t('app.name');
    }, [locale, t]);

    const value = useMemo<I18n>(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

    return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18n {
    const value = useContext(I18nContext);
    if (value === null) {
        throw new Error('useI18n must be used inside an I18nProvider');
    }
    return value;
}
