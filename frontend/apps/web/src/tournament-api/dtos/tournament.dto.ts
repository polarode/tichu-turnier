import { z } from 'zod';

import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/**
 * zod schemas describing the tournament wire format. Every response is parsed through
 * these at the repository boundary, so a backend change that breaks the contract fails
 * loudly instead of propagating `any` through the app.
 */
export const tournamentSettingsDtoSchema = z.object({
    allowGrandTichu: z.boolean(),
});

export type TournamentSettingsDto = z.infer<typeof tournamentSettingsDtoSchema>;

export const tournamentDtoSchema = z.object({
    id: z.string(),
    name: z.string(),
    description: z.string(),
    maxTeams: z.number(),
    settings: tournamentSettingsDtoSchema,
    status: z.enum([
        TournamentStatus.SETUP,
        TournamentStatus.ACTIVE,
        TournamentStatus.COMPLETED,
    ]),
    createdAt: z.string(),
    updatedAt: z.string(),
});

export const tournamentListDtoSchema = z.array(tournamentDtoSchema);

export type TournamentDto = z.infer<typeof tournamentDtoSchema>;

/**
 * The editable tournament payload shared by create and update (PUT replaces every
 * editable field), mirroring the backend's single write request.
 */
export interface TournamentWriteRequestDto {
    name: string;
    description: string;
    maxTeams: number;
    settings: TournamentSettingsDto;
}
