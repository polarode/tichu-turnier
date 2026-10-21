import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Form schema for creating and editing a tournament, built per locale so its messages
 * are localized. Mirrors the backend rules (required name, positive max teams) plus
 * UX-friendly bounds — the server still validates every submission.
 */
export function tournamentFormSchema(t: I18n['t']) {
    return z.object({
        name: z
            .string()
            .min(1, t('validation.tournamentName.required'))
            .max(100, t('validation.tournamentName.max')),
        description: z.string().max(500, t('validation.tournamentDescription.max')),
        maxTeams: z
            .number({ message: t('validation.tournamentMaxTeams.required') })
            .int(t('validation.tournamentMaxTeams.integer'))
            .min(2, t('validation.tournamentMaxTeams.min'))
            .max(64, t('validation.tournamentMaxTeams.max')),
        allowGrandTichu: z.boolean(),
    });
}

export type TournamentFormValues = z.infer<ReturnType<typeof tournamentFormSchema>>;
