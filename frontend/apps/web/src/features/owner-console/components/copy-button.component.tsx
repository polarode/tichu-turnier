import { Button } from '@tichu/ui/button.tsx';
import { useState } from 'react';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Copies text to the clipboard and confirms it inline. The clipboard API is absent in
 * some browsers/tests, so a failure is swallowed — the value stays visible in the
 * read-only input regardless, which is the actual fallback for copying.
 */
export function CopyButton({ value }: { value: string }) {
    const { t } = useI18n();
    const [copied, setCopied] = useState(false);

    const onClick = async () => {
        try {
            await navigator.clipboard?.writeText(value);
        } catch {
            // Clipboard access can be denied; the input still lets the user copy manually.
        }
        setCopied(true);
    };

    return (
        <Button type="button" variant="outline" size="sm" onClick={() => void onClick()}>
            {copied ? t('ownerConsole.invites.copied') : t('ownerConsole.invites.copy')}
        </Button>
    );
}
