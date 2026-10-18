import type { InputHTMLAttributes } from 'react';

import { Input } from '@tichu/ui/input.tsx';

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
    id: string;
    /** Already-localized label text. */
    label: string;
    /** Already-localized error text; when present it is announced to screen readers. */
    error?: string | undefined;
}

/** A labelled text input with an accessible, localized error message underneath it. */
export function FormField({ id, label, error, ...inputProps }: FormFieldProps) {
    const errorId = `${id}-error`;

    return (
        <div className="flex flex-col gap-1.5">
            <label htmlFor={id} className="text-sm font-medium">
                {label}
            </label>
            <Input
                id={id}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? errorId : undefined}
                {...inputProps}
            />
            {error ? (
                <p id={errorId} role="alert" className="text-sm text-destructive">
                    {error}
                </p>
            ) : null}
        </div>
    );
}
