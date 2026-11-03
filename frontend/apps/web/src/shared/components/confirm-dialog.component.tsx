import type { ReactNode } from 'react';
import { Button } from '@tichu/ui/button.tsx';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@tichu/ui/dialog.tsx';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

interface ConfirmDialogProps {
    titleKey: TranslationKey;
    descriptionKey?: TranslationKey;
    /** Rich description overriding `descriptionKey` (e.g. text interpolating a name). */
    description?: ReactNode;
    confirmLabelKey: TranslationKey;
    destructive: boolean;
    disabled: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

/**
 * Shared shape of the confirmations: title, consequence text, cancel + confirm.
 * Escape and a click on the backdrop cancel, like the Cancel button.
 */
export function ConfirmDialog({
    titleKey,
    descriptionKey,
    description,
    confirmLabelKey,
    destructive,
    disabled,
    onConfirm,
    onCancel,
}: ConfirmDialogProps) {
    const { t } = useI18n();
    const text = description ?? (descriptionKey ? t(descriptionKey) : null);
    return (
        <Dialog open onOpenChange={(open) => (open ? undefined : onCancel())}>
            {/* Without a description, opt out of aria-describedby rather than point at nothing. */}
            <DialogContent {...(text === null ? { 'aria-describedby': undefined } : {})}>
                <DialogHeader>
                    <DialogTitle>{t(titleKey)}</DialogTitle>
                    {text !== null ? <DialogDescription>{text}</DialogDescription> : null}
                </DialogHeader>
                <DialogFooter>
                    <Button variant="outline" onClick={onCancel}>
                        {t('common.cancel')}
                    </Button>
                    <Button
                        variant={destructive ? 'destructive' : 'default'}
                        disabled={disabled}
                        onClick={onConfirm}
                    >
                        {t(confirmLabelKey)}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
