import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Form schema for adding a co-organizer by account email, built per locale so its
 * messages are localized. It checks only the shape of the address; whether an active
 * admin account has that email is decided by the server.
 */
export function coOrganizerSchema(t: I18n['t']) {
    return z.object({
        email: z
            .string()
            .trim()
            .min(1, t('validation.email.required'))
            .pipe(z.email(t('validation.email.invalid'))),
    });
}

export type CoOrganizerFormValues = z.infer<ReturnType<typeof coOrganizerSchema>>;
