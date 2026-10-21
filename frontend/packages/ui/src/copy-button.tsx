import { Button } from './button.tsx';
import { useState } from 'react';

interface CopyButtonProps {
    /** The text to write to the clipboard. */
    value: string;
    /** Button label before copying. */
    copyLabel: string;
    /** Button label after copying. */
    copiedLabel: string;
    /** Optional Button size. */
    size?: 'sm' | 'default';
}

/**
 * Copies text to the clipboard and confirms it inline. Labels are passed in by the
 * caller (this package has no i18n), matching the primitive style of the other
 * components here.
 */
export function CopyButton({ value, copyLabel, copiedLabel, size = 'default' }: CopyButtonProps) {
    const [copied, setCopied] = useState(false);

    const onClick = async () => {
        try {
            // The clipboard API is absent in some browsers/tests — swallow and let the
            // shown value serve as the manual-copy fallback.
            await navigator.clipboard?.writeText(value);
        } catch {
            // Clipboard access can be denied; the shown value still allows manual copy.
        }
        setCopied(true);
    };

    return (
        <Button type="button" variant="outline" size={size} onClick={() => void onClick()}>
            {copied ? copiedLabel : copyLabel}
        </Button>
    );
}
