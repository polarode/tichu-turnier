/** Names of the events on the organizer's live tournament stream. Mirrors the backend. */
export const TournamentEventType = {
    GAME_RECORDED: 'GAME_RECORDED',
} as const;

export type TournamentEventType = (typeof TournamentEventType)[keyof typeof TournamentEventType];
