import type { ComponentProps } from 'react';

import { cn } from './lib/utils.ts';

export type SelectProps = ComponentProps<'select'>;

/** A native dropdown styled like [Input]. */
export function Select({ className, ...props }: SelectProps) {
    return (
        <select
            className={cn(
                'flex h-9 w-full rounded-md border border-input bg-card px-3 py-1 text-sm shadow-sm transition-colors focus-visible:ring-2 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50',
                className,
            )}
            {...props}
        />
    );
}
