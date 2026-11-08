/** Names of the events on the organizer's live tournament stream. Mirrors the backend. */
export const TournamentEventType = {
    GAME_RECORDED: 'GAME_RECORDED',
    TABLE_CONFIRMED: 'TABLE_CONFIRMED',
    CONFIRMATION_RETRACTED: 'CONFIRMATION_RETRACTED',
    TOURNAMENT_STARTED: 'TOURNAMENT_STARTED',
    ROUND_STARTED: 'ROUND_STARTED',
    TOTAL_ROUNDS_CHANGED: 'TOTAL_ROUNDS_CHANGED',
    TOURNAMENT_FINISHED: 'TOURNAMENT_FINISHED',
    RESULTS_PUBLISHED: 'RESULTS_PUBLISHED',
    ORGANIZERS_CHANGED: 'ORGANIZERS_CHANGED',
} as const;

export type TournamentEventType = (typeof TournamentEventType)[keyof typeof TournamentEventType];
