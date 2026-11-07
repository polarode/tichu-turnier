import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Open/close state of a header dropdown (menu or popover) anchored to a trigger
 * button. Attach `rootRef` to the positioning root that wraps trigger and panel,
 * and `triggerRef` to the trigger. Outside clicks close quietly; Escape closes
 * and hands focus back to the trigger.
 */
export function useHeaderPopover() {
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);

    const close = useCallback((returnFocus: boolean) => {
        setOpen(false);
        if (returnFocus) triggerRef.current?.focus();
    }, []);
    const toggle = useCallback(() => setOpen((wasOpen) => !wasOpen), []);

    useEffect(() => {
        if (!open) return;
        function onDocumentClick(event: MouseEvent) {
            if (!rootRef.current?.contains(event.target as Node)) close(false);
        }
        function onKeyDown(event: KeyboardEvent) {
            if (event.key === 'Escape') close(true);
        }
        document.addEventListener('mousedown', onDocumentClick);
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('mousedown', onDocumentClick);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [open, close]);

    return { open, toggle, close, rootRef, triggerRef };
}
