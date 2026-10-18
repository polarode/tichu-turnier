interface FormAlertProps {
    /** Already-localized message; when absent the alert renders nothing. */
    message?: string | undefined;
}

/** Form-level server message, announced to screen readers. */
export function FormAlert({ message }: FormAlertProps) {
    if (!message) return null;

    return (
        <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {message}
        </p>
    );
}
