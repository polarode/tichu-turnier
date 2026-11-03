import { XIcon } from 'lucide-react';
import { Dialog as DialogPrimitive } from 'radix-ui';
import { type ComponentProps, useRef } from 'react';

import { cn } from './lib/utils.ts';

/**
 * shadcn/ui Dialog (Radix). Radix provides the modal behaviour: focus moves into
 * the dialog and is trapped there, Escape and a click on the backdrop close it
 * (through `onOpenChange`), and the rest of the page is hidden from screen
 * readers. Focus returns to the element that opened it: Radix alone only returns
 * it to a `DialogTrigger`, while ours are mostly controlled and opened by plain
 * buttons. The dialog is named by its `DialogTitle` and described by its
 * `DialogDescription`.
 */
export function Dialog(props: ComponentProps<typeof DialogPrimitive.Root>) {
    return <DialogPrimitive.Root data-slot="dialog" {...props} />;
}

export function DialogTrigger(props: ComponentProps<typeof DialogPrimitive.Trigger>) {
    return <DialogPrimitive.Trigger data-slot="dialog-trigger" {...props} />;
}

export function DialogClose(props: ComponentProps<typeof DialogPrimitive.Close>) {
    return <DialogPrimitive.Close data-slot="dialog-close" {...props} />;
}

interface DialogContentProps extends ComponentProps<typeof DialogPrimitive.Content> {
    /** Already-localized name of the corner close button; the button is omitted without it. */
    closeLabel?: string;
}

/** The dimmed backdrop plus the centered panel, rendered in a portal. */
export function DialogContent({
    className,
    children,
    closeLabel,
    onOpenAutoFocus,
    onCloseAutoFocus,
    ...props
}: DialogContentProps) {
    const opener = useRef<HTMLElement | null>(null);

    return (
        <DialogPrimitive.Portal>
            <DialogPrimitive.Overlay
                data-slot="dialog-overlay"
                className="fixed inset-0 z-50 bg-black/50"
            />
            <DialogPrimitive.Content
                data-slot="dialog-content"
                className={cn(
                    'fixed top-1/2 left-1/2 z-50 grid w-full max-w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 gap-4 rounded-md border border-border bg-card p-6 shadow-lg outline-none sm:max-w-sm',
                    className,
                )}
                onOpenAutoFocus={(event) => {
                    // Still the opener: Radix moves focus in only after this handler.
                    opener.current =
                        document.activeElement instanceof HTMLElement
                            ? document.activeElement
                            : null;
                    onOpenAutoFocus?.(event);
                }}
                onCloseAutoFocus={(event) => {
                    onCloseAutoFocus?.(event);
                    if (event.defaultPrevented || !opener.current?.isConnected) return;
                    event.preventDefault();
                    opener.current.focus();
                }}
                {...props}
            >
                {children}
                {closeLabel ? (
                    <DialogPrimitive.Close
                        data-slot="dialog-close"
                        className="absolute top-4 right-4 rounded-sm opacity-70 transition-opacity outline-none hover:opacity-100 focus-visible:ring-2"
                    >
                        <XIcon aria-hidden="true" className="size-4" />
                        <span className="sr-only">{closeLabel}</span>
                    </DialogPrimitive.Close>
                ) : null}
            </DialogPrimitive.Content>
        </DialogPrimitive.Portal>
    );
}

export function DialogHeader({ className, ...props }: ComponentProps<'div'>) {
    return (
        <div
            data-slot="dialog-header"
            className={cn('flex flex-col gap-1', className)}
            {...props}
        />
    );
}

/** The actions row: stacked on phones (primary on top), right-aligned from `sm` up. */
export function DialogFooter({ className, ...props }: ComponentProps<'div'>) {
    return (
        <div
            data-slot="dialog-footer"
            className={cn('flex flex-col-reverse gap-2 sm:flex-row sm:justify-end', className)}
            {...props}
        />
    );
}

export function DialogTitle({ className, ...props }: ComponentProps<typeof DialogPrimitive.Title>) {
    return (
        <DialogPrimitive.Title
            data-slot="dialog-title"
            className={cn('text-lg font-semibold', className)}
            {...props}
        />
    );
}

export function DialogDescription({
    className,
    ...props
}: ComponentProps<typeof DialogPrimitive.Description>) {
    return (
        <DialogPrimitive.Description
            data-slot="dialog-description"
            className={cn('text-sm text-muted-foreground', className)}
            {...props}
        />
    );
}
