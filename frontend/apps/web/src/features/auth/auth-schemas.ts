import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Form schemas are built per locale so their messages are localized. They mirror the
 * backend rules (email shape, password length) without becoming the source of truth:
 * the server still validates every submission.
 */
export function createLoginSchema(t: I18n['t']) {
    return z.object({
        email: z
            .string()
            .min(1, t('validation.email.required'))
            .pipe(z.email(t('validation.email.invalid'))),
        password: z.string().min(1, t('validation.password.required')),
    });
}

export function createRegisterSchema(t: I18n['t']) {
    return z.object({
        email: z
            .string()
            .min(1, t('validation.email.required'))
            .pipe(z.email(t('validation.email.invalid'))),
        // The backend enforces a minimum of 8 characters; mirror it for instant feedback.
        password: z.string().min(8, t('validation.password.min')),
    });
}

export type LoginValues = z.infer<ReturnType<typeof createLoginSchema>>;
export type RegisterValues = z.infer<ReturnType<typeof createRegisterSchema>>;
