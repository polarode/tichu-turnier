import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

import type { ApiErrorCode } from '@/shared/api/api-error.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { errorMessageKey } from '@/shared/i18n/error-messages.ts';
import type { I18n } from '@/shared/i18n/i18n.context.tsx';

interface ServerErrorAnchors<TValues extends FieldValues> {
    /** Backend problem `field` name → form field, for field-anchored problems. */
    fields?: Partial<Record<string, Path<TValues>>>;
    /** Backend error code → form field, for codes that carry no `field` of their own. */
    codes?: Partial<Record<ApiErrorCode, Path<TValues>>>;
}

/**
 * Localizes a typed API failure (or any unexpected error) into a user-facing message.
 * Shared by form-level error routing and by non-form actions (console row buttons).
 */
export function localizedErrorMessage(error: unknown, t: I18n['t']): string {
    return error instanceof ApiError ? t(errorMessageKey(error.code)) : t('error.unknown');
}

/**
 * Routes a typed API failure into a react-hook-form. A problem that can be anchored to
 * an input — by its `field` member or, failing that, by its error code — lands there;
 * anything else becomes a form-level message under `root`. Returns the localized
 * message so callers can also surface it without reading form state.
 */
export function applyServerError<TValues extends FieldValues>(
    setError: UseFormSetError<TValues>,
    error: unknown,
    t: I18n['t'],
    anchors: ServerErrorAnchors<TValues> = {},
): string {
    const message = localizedErrorMessage(error, t);
    const field =
        error instanceof ApiError
            ? ((error.field ? anchors.fields?.[error.field] : undefined) ?? anchors.codes?.[error.code])
            : undefined;

    if (field) {
        setError(field, { type: 'server', message });
    } else {
        setError('root', { type: 'server', message });
    }

    return message;
}
