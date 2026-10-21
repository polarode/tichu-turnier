import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.ts';

/**
 * Form schema for adding and editing a team, built per locale so its messages are
 * localized. Mirrors the backend's input rules: no blank names and two distinct
 * players (compared case-insensitively, as the backend does). Cross-team duplicates
 * and the tournament's team capacity stay server-side — that is where the
 * authoritative check lives.
 */
export function teamFormSchema(t: I18n['t']) {
    return z
        .object({
            // `trim()` mirrors the backend: it rejects blank (whitespace-only) names and
            // the parsed output is trimmed, as the backend trims before validating.
            name: z.string().trim().min(1, t('validation.teamName.required')),
            playerOneName: z.string().trim().min(1, t('validation.playerName.required')),
            playerTwoName: z.string().trim().min(1, t('validation.playerName.required')),
        })
        .refine(
            ({ playerOneName, playerTwoName }) =>
                playerOneName.trim().toLowerCase() !== playerTwoName.trim().toLowerCase(),
            { path: ['playerTwoName'], message: t('error.duplicateTeamPlayers') },
        );
}

export type TeamFormValues = z.infer<ReturnType<typeof teamFormSchema>>;
