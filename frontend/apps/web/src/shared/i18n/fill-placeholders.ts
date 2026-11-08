/** Replaces each `{name}` in the catalog text with its value; unknown placeholders stay as they are. */
export function fillPlaceholders(template: string, values: Record<string, string> = {}): string {
    return Object.entries(values).reduce(
        (text, [name, value]) => text.replaceAll(`{${name}}`, value),
        template,
    );
}
