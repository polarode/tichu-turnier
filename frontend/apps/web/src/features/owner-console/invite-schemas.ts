import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The invite form schema, built per locale so its messages are localized. It mirrors
 * the backend's label rule without becoming the source of truth: the server validates
 * every submission again.
 */
export function createInviteSchema(t: I18n['t']) {
    return z.object({
        label: z
            .string()
            .trim()
            .min(1, t('validation.inviteLabel.required'))
            .max(100, t('validation.inviteLabel.max')),
    });
}

export type InviteValues = z.infer<ReturnType<typeof createInviteSchema>>;
