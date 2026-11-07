import { useEffect, useRef, type KeyboardEvent, type ReactNode } from 'react';

import { useHeaderPopover } from '@/shared/hooks/use-header-popover.hook.ts';

export interface HeaderMenuOption<T extends string> {
    value: T;
    label: string;
    /** Decorative leading glyph (icon or flag); hidden from assistive tech. */
    icon: ReactNode;
}

interface HeaderMenuProps<T extends string> {
    /** Accessible name of both the trigger and the menu. */
    label: string;
    options: readonly HeaderMenuOption<T>[];
    value: T;
    onValueChange: (value: T) => void;
    /** Visible trigger content: the current choice as an icon or flag. */
    trigger: ReactNode;
}

/**
 * A single-choice menu in the header: the trigger shows the current choice and
 * announces it as its description; the menu lists every option as a
 * `menuitemradio`. Keyboard follows the WAI-ARIA menu pattern: opening focuses
 * the checked option, Arrow Up/Down wrap, Home/End jump, Escape and choosing
 * close the menu and return focus to the trigger.
 */
export function HeaderMenu<T extends string>({
    label,
    options,
    value,
    onValueChange,
    trigger,
}: HeaderMenuProps<T>) {
    // Outside clicks close quietly; Escape closes and returns focus to the trigger.
    const { open, toggle, close, rootRef, triggerRef } = useHeaderPopover();
    const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

    const current = options.find((option) => option.value === value);
    const checkedIndex = options.findIndex((option) => option.value === value);

    // Opening moves focus onto the checked option (or the first one).
    useEffect(() => {
        if (open) itemRefs.current[Math.max(checkedIndex, 0)]?.focus();
        // Only on opening: re-running on a value change would yank focus mid-navigation.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open]);

    const onMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        const items = itemRefs.current.filter((item): item is HTMLButtonElement => item !== null);
        const index = items.indexOf(document.activeElement as HTMLButtonElement);
        const last = items.length - 1;
        const target =
            event.key === 'ArrowDown' ? (index >= last ? 0 : index + 1)
            : event.key === 'ArrowUp' ? (index <= 0 ? last : index - 1)
            : event.key === 'Home' ? 0
            : event.key === 'End' ? last
            : null;
        if (event.key === 'Tab') {
            close(false);
            return;
        }
        if (target === null) return;
        event.preventDefault();
        items[target]?.focus();
    };

    return (
        <div ref={rootRef} className="relative">
            <button
                ref={triggerRef}
                type="button"
                aria-label={label}
                aria-haspopup="menu"
                aria-expanded={open}
                aria-description={current?.label}
                onClick={toggle}
                className="flex size-10 items-center justify-center rounded-md text-lg leading-none text-header-muted transition-colors outline-none hover:bg-header-active hover:text-header-foreground focus-visible:ring-2 focus-visible:ring-header-foreground"
            >
                {trigger}
            </button>

            {open && (
                <div
                    role="menu"
                    aria-label={label}
                    onKeyDown={onMenuKeyDown}
                    className="absolute right-0 z-10 mt-1 min-w-36 overflow-hidden rounded-md border border-border bg-popover text-popover-foreground shadow-md"
                >
                    {options.map((option, index) => {
                        const checked = option.value === value;
                        return (
                            <button
                                key={option.value}
                                ref={(element) => {
                                    itemRefs.current[index] = element;
                                }}
                                type="button"
                                role="menuitemradio"
                                aria-checked={checked}
                                tabIndex={-1}
                                onClick={() => {
                                    onValueChange(option.value);
                                    close(true);
                                }}
                                className="flex w-full items-center gap-2 px-3 py-2 text-sm transition-colors outline-none hover:bg-accent hover:text-accent-foreground focus-visible:bg-accent focus-visible:text-accent-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
                            >
                                <span aria-hidden="true" className="flex size-4 items-center justify-center">
                                    {option.icon}
                                </span>
                                <span>{option.label}</span>
                                {checked && (
                                    <span aria-hidden="true" className="ml-auto">
                                        ✓
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}
