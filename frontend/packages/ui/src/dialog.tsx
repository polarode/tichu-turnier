import type { ReactNode } from 'react';

interface DialogProps {
    /** Whether the dialog is rendered. */
    open: boolean;
    /** Accessible name announced to screen readers (use a translation key). */
    label: string;
    /** Optional close handler for the backdrop; dialogs may also close themselves. */
    onClose?: () => void;
    children: ReactNode;
}

/**
 * Minimal modal dialog: a dimmed backdrop with a centered, labelled panel. Styling
 * only — state, focus management and actions stay with the caller, matching the
 * primitive style of the other components in this package.
 */
export function Dialog({ open, label, onClose, children }: DialogProps) {
    if (!open) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
            onClick={onClose}
        >
            <div
                role="dialog"
                aria-modal="true"
                aria-label={label}
                onClick={(event) => event.stopPropagation()}
                className="w-full max-w-sm rounded-md border border-border bg-background p-6 shadow-lg"
            >
                {children}
            </div>
        </div>
    );
}
