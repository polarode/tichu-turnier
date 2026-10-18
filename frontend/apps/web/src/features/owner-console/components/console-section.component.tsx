import type { ReactNode } from 'react';

import { FormAlert } from '@/shared/components/form-alert.component.tsx';

interface ConsoleSectionProps {
    /** Already-localized heading. */
    title: string;
    /** Already-localized supporting line. */
    description: string;
    /** Localized action failure, shown above the section body. */
    error?: string | undefined;
    children: ReactNode;
}

/** Consistent heading, description and error slot shared by the console's sections. */
export function ConsoleSection({ title, description, error, children }: ConsoleSectionProps) {
    return (
        <section className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
                <h2 className="text-xl font-semibold">{title}</h2>
                <p className="text-sm text-muted-foreground">{description}</p>
            </div>
            <FormAlert message={error} />
            {children}
        </section>
    );
}
