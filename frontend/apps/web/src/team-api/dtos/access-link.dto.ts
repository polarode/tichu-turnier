import { z } from 'zod';

/**
 * zod schema for the one response that ever carries raw link material: the reveal
 * response. Every other endpoint keeps the token server-side, so nothing else in
 * `team-api` may accept a token field.
 */
export const accessLinkRevealDtoSchema = z.object({
    teamId: z.string(),
    tournamentId: z.string(),
    token: z.string(),
    link: z.string(),
});

export type AccessLinkRevealDto = z.infer<typeof accessLinkRevealDtoSchema>;
