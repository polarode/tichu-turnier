import { useEffect, type RefObject } from 'react';

/**
 * Closes an open dropdown: outside clicks and Escape. Attach the returned state
 * flag to the component's open state and the ref to its positioning root.
 */
export function useDropdownDismiss(
    open: boolean,
    rootRef: RefObject<HTMLElement | null>,
    onClose: () => void,
): void {
    useEffect(() => {
        if (!open) return;
        function onDocumentClick(event: MouseEvent) {
            if (!rootRef.current?.contains(event.target as Node)) onClose();
        }
        function onKeyDown(event: KeyboardEvent) {
            if (event.key === 'Escape') onClose();
        }
        document.addEventListener('mousedown', onDocumentClick);
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('mousedown', onDocumentClick);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [open, rootRef, onClose]);
}
