import type { ReactNode } from 'react';

/** One account or invite line inside a console list; the caller supplies its contents. */
export function ConsoleRow({ children }: { children: ReactNode }) {
    return (
        <li className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border p-3">
            {children}
        </li>
    );
}
