/**
 * Lifecycle of a tournament. Mirrors the backend enum: `setup → active → completed →
 * published`, final. Shared by the domain model and the DTO schema so both reference one source.
 */
export const TournamentStatus = {
    SETUP: 'SETUP',
    ACTIVE: 'ACTIVE',
    COMPLETED: 'COMPLETED',
    PUBLISHED: 'PUBLISHED',
} as const;

export type TournamentStatus = (typeof TournamentStatus)[keyof typeof TournamentStatus];

export const TOURNAMENT_STATUSES = [
    TournamentStatus.SETUP,
    TournamentStatus.ACTIVE,
    TournamentStatus.COMPLETED,
    TournamentStatus.PUBLISHED,
] as const;
