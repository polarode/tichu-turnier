import { z } from 'zod';

import { gameFieldsSchema, type GameFormValues } from '@/features/scoring/scoring-schemas.ts';
import type { TeamTable } from '@/domain/game.domain.ts';

/**
 * An unsaved game entry kept on this device, so a reload or a discarded tab on a phone does
 * not lose it. [baseVersion] is the stored version the entry started from (null for a game
 * not saved yet), so a draft that a newer saved game overtook can be recognised.
 */
export interface GameDraft {
    baseVersion: number | null;
    values: GameFormValues;
}

const gameDraftSchema = z.object({
    baseVersion: z.number().int().nullable(),
    values: gameFieldsSchema,
});

/** localStorage key of one game's draft at one table of one tournament. */
export function gameDraftKey(
    table: Pick<TeamTable, 'tournamentId' | 'roundNumber' | 'tableNumber'>,
    gameNumber: number,
): string {
    return `tichu.gameDraft.${table.tournamentId}.${table.roundNumber}.${table.tableNumber}.${gameNumber}`;
}

/**
 * Best-effort localStorage access, like the stored preferences: reads validate the stored
 * draft and every direction swallows storage errors, so blocked or full storage never
 * breaks the entry form.
 */
export function readGameDraft(key: string): GameDraft | null {
    if (typeof window === 'undefined') return null;
    try {
        const stored = window.localStorage.getItem(key);
        if (stored === null) return null;
        const parsed = gameDraftSchema.safeParse(JSON.parse(stored));
        return parsed.success ? parsed.data : null;
    } catch {
        return null;
    }
}

export function storeGameDraft(key: string, draft: GameDraft): void {
    if (typeof window === 'undefined') return;
    try {
        window.localStorage.setItem(key, JSON.stringify(draft));
    } catch {
        // Keeping the draft is best-effort; never break the entry form over it.
    }
}

export function clearGameDraft(key: string): void {
    if (typeof window === 'undefined') return;
    try {
        window.localStorage.removeItem(key);
    } catch {
        // Nothing stored, or storage blocked: there is no draft to clear.
    }
}
