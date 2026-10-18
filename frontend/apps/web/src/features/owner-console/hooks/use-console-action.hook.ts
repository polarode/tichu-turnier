import { useState } from 'react';

import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import type { I18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Runs a console row action and captures its localized failure. Approve, reject,
 * disable, reactivate and revoke all share this shape, so the state handling lives
 * here once instead of in every section.
 */
export function useConsoleAction(t: I18n['t']) {
    const [error, setError] = useState<string>();

    const run = async (action: () => Promise<unknown>) => {
        setError(undefined);
        try {
            await action();
        } catch (failure) {
            setError(localizedErrorMessage(failure, t));
        }
    };

    return { error, run };
}
