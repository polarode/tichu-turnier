import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes, KeyboardEvent, ReactNode } from 'react';

import { buttonVariants } from './button.tsx';
import { cn } from './lib/utils.ts';

const tabListVariants = cva('', {
    variants: {
        variant: {
            /** Equal-width buttons, one per tab: game and round pickers. */
            segmented: 'grid auto-cols-fr grid-flow-col gap-2',
            /** Text tabs over a bottom rule: switching between page sections. */
            underline: 'flex gap-1 overflow-x-auto border-b border-border',
        },
    },
    defaultVariants: { variant: 'segmented' },
});

const underlineTabClass =
    'inline-flex shrink-0 items-center gap-2 border-b-2 px-3 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50';

export interface TabItem<T extends string> {
    value: T;
    label: ReactNode;
    /** Segmented only: marks a finished item (entered game, complete round) with the secondary fill. */
    done?: boolean;
}

export type TabListProps<T extends string> = Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> &
    VariantProps<typeof tabListVariants> & {
        /** Prefix for the tab and panel element ids; must be unique on the page. */
        id: string;
        /** Accessible name of the tab list. */
        label: string;
        items: readonly TabItem<T>[];
        value: T;
        onValueChange: (value: T) => void;
        /** `touch` makes every tab 44px tall for player-facing screens. */
        size?: 'default' | 'touch';
    };

export const tabId = (id: string, value: string) => `${id}-tab-${value}`;
const panelId = (id: string) => `${id}-panel`;

/**
 * WAI-ARIA tabs with automatic activation: only the selected tab is in the Tab
 * order, and Arrow Left/Right, Home and End move the selection and focus. Pair it
 * with one {@link TabPanel} sharing the same `id`, which renders the active content.
 */
export function TabList<T extends string>({
    id,
    label,
    items,
    value,
    onValueChange,
    variant,
    size = 'default',
    className,
    ...props
}: TabListProps<T>) {
    const select = (index: number) => {
        const item = items[index];
        if (!item) return;
        onValueChange(item.value);
        document.getElementById(tabId(id, item.value))?.focus();
    };

    const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
        const last = items.length - 1;
        const target =
            event.key === 'ArrowRight'
                ? index === last
                    ? 0
                    : index + 1
                : event.key === 'ArrowLeft'
                  ? index === 0
                      ? last
                      : index - 1
                  : event.key === 'Home'
                    ? 0
                    : event.key === 'End'
                      ? last
                      : null;
        if (target === null) return;
        event.preventDefault();
        select(target);
    };

    return (
        <div
            role="tablist"
            aria-label={label}
            className={cn(tabListVariants({ variant }), className)}
            {...props}
        >
            {items.map((item, index) => {
                const selected = item.value === value;
                return (
                    <button
                        key={item.value}
                        id={tabId(id, item.value)}
                        type="button"
                        role="tab"
                        aria-selected={selected}
                        aria-controls={panelId(id)}
                        tabIndex={selected ? 0 : -1}
                        onClick={() => onValueChange(item.value)}
                        onKeyDown={(event) => onKeyDown(event, index)}
                        className={
                            variant === 'underline'
                                ? cn(
                                      underlineTabClass,
                                      size === 'touch' ? 'h-11' : 'h-10',
                                      selected
                                          ? 'border-primary text-foreground'
                                          : 'border-transparent text-muted-foreground hover:text-foreground',
                                  )
                                : buttonVariants({
                                      variant: selected
                                          ? 'default'
                                          : item.done
                                            ? 'secondary'
                                            : 'outline',
                                      size,
                                      className: 'px-2',
                                  })
                        }
                    >
                        {item.label}
                    </button>
                );
            })}
        </div>
    );
}

export type TabPanelProps = HTMLAttributes<HTMLDivElement> & {
    /** The same `id` as the controlling {@link TabList}. */
    id: string;
    /** The currently selected tab value; names the panel after its tab. */
    value: string;
};

/** The content area of a {@link TabList}; renders whatever the active tab shows. */
export function TabPanel({ id, value, className, children, ...props }: TabPanelProps) {
    return (
        <div
            role="tabpanel"
            id={panelId(id)}
            aria-labelledby={tabId(id, value)}
            className={className}
            {...props}
        >
            {children}
        </div>
    );
}
