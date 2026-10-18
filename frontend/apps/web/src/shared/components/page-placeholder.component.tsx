import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface PagePlaceholderProps {
    titleKey: TranslationKey;
    descriptionKey: TranslationKey;
}

/** Temporary screen body for the route skeletons; later phases replace each one. */
export function PagePlaceholder({ titleKey, descriptionKey }: PagePlaceholderProps) {
    const { t } = useI18n();

    return (
        <section className="flex flex-col gap-2">
            <h1 className="text-2xl font-semibold">{t(titleKey)}</h1>
            <p className="text-muted-foreground">{t(descriptionKey)}</p>
        </section>
    );
}
