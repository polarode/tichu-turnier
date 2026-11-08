import { Popover as PopoverPrimitive } from 'radix-ui';
import type { ComponentProps } from 'react';

import { cn } from './lib/utils.ts';

/**
 * shadcn/ui Popover (Radix). A floating panel anchored to its trigger and rendered in
 * a portal: a click on the trigger opens it, Escape or an outside click closes it and
 * returns focus to the trigger.
 */
export function Popover(props: ComponentProps<typeof PopoverPrimitive.Root>) {
    return <PopoverPrimitive.Root data-slot="popover" {...props} />;
}

export function PopoverTrigger(props: ComponentProps<typeof PopoverPrimitive.Trigger>) {
    return <PopoverPrimitive.Trigger data-slot="popover-trigger" {...props} />;
}

/** The open panel, anchored under the trigger (or a PopoverAnchor) and rendered in a portal. */
export function PopoverContent({
    className,
    align = 'center',
    sideOffset = 4,
    ...props
}: ComponentProps<typeof PopoverPrimitive.Content>) {
    return (
        <PopoverPrimitive.Portal>
            <PopoverPrimitive.Content
                data-slot="popover-content"
                align={align}
                sideOffset={sideOffset}
                className={cn(
                    'z-50 w-72 origin-(--radix-popover-content-transform-origin) rounded-md border bg-popover p-4 text-popover-foreground shadow-md outline-hidden',
                    className,
                )}
                {...props}
            />
        </PopoverPrimitive.Portal>
    );
}

/** Positions the panel against an element other than the trigger. */
export function PopoverAnchor(props: ComponentProps<typeof PopoverPrimitive.Anchor>) {
    return <PopoverPrimitive.Anchor data-slot="popover-anchor" {...props} />;
}
