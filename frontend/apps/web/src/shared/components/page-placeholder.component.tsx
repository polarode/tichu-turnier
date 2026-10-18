import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

interface PagePlaceholderProps {
    titleKey: TranslationKey;
    descriptionKey: TranslationKey;
}

/** Temporary screen body for the route skeletons; later phases replace each one. */
export function PagePlaceholder({ titleKey, descriptionKey }: PagePlaceholderProps) {
    return (
        <section className="flex flex-col gap-2">
            <PageHeader titleKey={titleKey} descriptionKey={descriptionKey} />
        </section>
    );
}
