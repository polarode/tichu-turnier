import { z } from 'zod';

import type { I18n } from '@/shared/i18n/i18n.context.tsx';
import { doubleWinTeam } from '@/features/scoring/game-form.model.ts';

/**
 * Form schema for the game entry grid, built per locale so its messages are
 * localized. It mirrors the backend's structural rules so the obvious mistakes
 * surface instantly (client-side), while the authoritative scoring validation
 * stays server-side (ADR 0004): every player has finished, the four positions are
 * unique 1..4, and — the double-win exception — two winners hold 1 and 2 while the
 * two losers have no position. All other scoring math is display-only and recomputed
 * by the server.
 */
export function gameFormSchema(t: I18n['t']) {
    const call = z.enum(['NONE', 'SMALL', 'GRAND']);
    const quad = <T extends z.ZodTypeAny>(item: T) => z.tuple([item, item, item, item]);

    return z
        .object({
            positions: quad(z.number().int().min(1).max(4).nullable()),
            calls: quad(call),
            bombs: quad(z.number().int().min(0).max(3)),
            baseScoreTeam0: z.number().int(),
            beschiss: z.boolean(),
        })
        .superRefine((value, ctx) => {
            const positions = value.positions;
            const assigned = positions.filter((p): p is number => p !== null);
            const dw = doubleWinTeam(positions);

            if (dw !== null) {
                // A double win: exactly positions 1 and 2 assigned (to the winners),
                // the two losers unset. The winners-same-team check is doubleWinTeam's.
                if (assigned.length !== 2 || [...assigned].sort().join() !== '1,2') {
                    ctx.addIssue({
                        code: z.ZodIssueCode.custom,
                        path: ['positions'],
                        message: t('validation.scoring.positions'),
                    });
                }
                return;
            }

            // A normal game: all four players finished, positions a permutation of 1..4.
            if (assigned.length !== 4 || [...assigned].sort().join() !== '1,2,3,4') {
                ctx.addIssue({
                    code: z.ZodIssueCode.custom,
                    path: ['positions'],
                    message: t('validation.scoring.positions'),
                });
            }
        });
}

export type GameFormValues = z.infer<ReturnType<typeof gameFormSchema>>;
