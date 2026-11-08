import { Monitor, Moon, Sun, type LucideIcon } from 'lucide-react';

import { HeaderMenu } from '@/shared/components/header-menu.component.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { useTheme } from '@/shared/theme/theme.context.tsx';
import { THEME_PREFERENCES, type ThemePreference } from '@/shared/theme/theme.ts';

const THEME_LABEL_KEY: Record<ThemePreference, TranslationKey> = {
    light: 'theme.light',
    dark: 'theme.dark',
    system: 'theme.system',
};

const THEME_ICON: Record<ThemePreference, LucideIcon> = {
    light: Sun,
    dark: Moon,
    system: Monitor,
};

/** Icon button in the header next to the language flag; its menu picks light, dark or system. */
export function ThemeSwitcher() {
    const { t } = useI18n();
    const { preference, setPreference } = useTheme();
    const TriggerIcon = THEME_ICON[preference];

    return (
        <HeaderMenu
            label={t('theme.label')}
            value={preference}
            onValueChange={setPreference}
            trigger={<TriggerIcon aria-hidden="true" className="size-5" />}
            options={THEME_PREFERENCES.map((option) => {
                const Icon = THEME_ICON[option];
                return {
                    value: option,
                    label: t(THEME_LABEL_KEY[option]),
                    icon: <Icon className="size-4" />,
                };
            })}
        />
    );
}
