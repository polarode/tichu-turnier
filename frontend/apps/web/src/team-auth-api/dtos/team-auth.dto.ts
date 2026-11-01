import { z } from 'zod';

import { TOURNAMENT_STATUSES } from '@/vocabulary/tournament-status.enum.ts';

/**
 * zod schema for the exchange response. Unlike the access-link reveal, this carries
 * no secret — the session token travels in an httpOnly `Set-Cookie` header, so the
 * body only holds non-sensitive session metadata.
 */
export const teamSessionDtoSchema = z.object({
    teamId: z.string(),
    tournamentId: z.string(),
    /** Backend `Instant` serialized as ISO-8601 UTC. */
    expiresAt: z.string().datetime({ offset: true }),
});

export type TeamSessionDto = z.infer<typeof teamSessionDtoSchema>;

/**
 * zod schema for the session-guarded team overview (`GET /api/team/me`). Player ids
 * beyond `id`/`name` are transport detail and dropped by the mapper.
 */
export const teamOverviewDtoSchema = z.object({
    teamId: z.string(),
    tournamentId: z.string(),
    tournamentName: z.string(),
    /** Lifecycle of the tournament the team plays in (`setup → active → completed`). */
    tournamentStatus: z.enum(TOURNAMENT_STATUSES),
    teamName: z.string(),
    players: z
        .object({
            id: z.string(),
            teamId: z.string(),
            name: z.string(),
        })
        .array()
        // A team is exactly two named players, server-enforced since S1.
        .length(2),
});

export type TeamOverviewDto = z.infer<typeof teamOverviewDtoSchema>;
