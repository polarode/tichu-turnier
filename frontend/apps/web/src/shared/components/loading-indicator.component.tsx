import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Router pending state: shown while a `beforeLoad` await is in flight. */
export function LoadingIndicator() {
    const { t } = useI18n();
    return (
        <div className="flex flex-1 items-center justify-center py-16" role="status">
            <span className="text-muted-foreground">{t('common.loading')}</span>
        </div>
    );
}
