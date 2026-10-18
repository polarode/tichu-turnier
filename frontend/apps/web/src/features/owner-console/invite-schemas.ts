import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The invite form schema, built per locale so its messages are localized. It mirrors
 * the backend's email rule without becoming the source of truth: the server validates
 * every submission again.
 */
export function createInviteSchema(t: I18n['t']) {
    return z.object({
        email: z
            .string()
            .min(1, t('validation.email.required'))
            .pipe(z.email(t('validation.email.invalid'))),
    });
}

export type InviteValues = z.infer<ReturnType<typeof createInviteSchema>>;
