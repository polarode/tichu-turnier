import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Shown to a signed-in admin who is not a platform owner (and, defensively, when an
 * owner-only request unexpectedly answers 403). The backend refuses the data either
 * way, so this only states the outcome in the user's language.
 */
export function OwnerConsoleForbidden() {
    const { t } = useI18n();

    return (
        <section className="flex max-w-md flex-col gap-2">
            <h1 className="text-2xl font-semibold">{t('ownerConsole.forbidden.title')}</h1>
            <p className="text-muted-foreground">{t('ownerConsole.forbidden.description')}</p>
        </section>
    );
}
