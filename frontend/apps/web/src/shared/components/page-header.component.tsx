import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface PageHeaderProps {
    titleKey: TranslationKey;
    descriptionKey: TranslationKey;
}

/** Localized page title and description, shared by the standalone screens. */
export function PageHeader({ titleKey, descriptionKey }: PageHeaderProps) {
    const { t } = useI18n();

    return (
        <div className="flex flex-col gap-2">
            <h1 className="text-2xl font-semibold">{t(titleKey)}</h1>
            <p className="text-muted-foreground">{t(descriptionKey)}</p>
        </div>
    );
}
