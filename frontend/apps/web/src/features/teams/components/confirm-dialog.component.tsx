import { Button } from '@tichu/ui/button.tsx';
import { Dialog } from '@tichu/ui/dialog.tsx';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

interface ConfirmDialogProps {
    titleKey: TranslationKey;
    descriptionKey: TranslationKey;
    confirmLabelKey: TranslationKey;
    destructive: boolean;
    disabled: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

/** Shared shape of the rotate/revoke confirmations: title, consequence text, cancel + confirm. */
export function ConfirmDialog({
    titleKey,
    descriptionKey,
    confirmLabelKey,
    destructive,
    disabled,
    onConfirm,
    onCancel,
}: ConfirmDialogProps) {
    const { t } = useI18n();
    return (
        <Dialog open label={t(titleKey)}>
            <h2 className="mb-2 text-lg font-semibold">{t(titleKey)}</h2>
            <p className="mb-4 text-sm text-muted-foreground">{t(descriptionKey)}</p>
            <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={onCancel}>
                    {t('common.cancel')}
                </Button>
                <Button variant={destructive ? 'destructive' : 'default'} disabled={disabled} onClick={onConfirm}>
                    {t(confirmLabelKey)}
                </Button>
            </div>
        </Dialog>
    );
}
