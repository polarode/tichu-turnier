import type { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/**
 * Tichu Points a grand Tichu call wins or loses: 200 under the normal rules, or 100 to
 * score it like a small Tichu.
 */
export const GRAND_TICHU_BONUS_OPTIONS = [100, 200] as const;
export type GrandTichuBonus = (typeof GRAND_TICHU_BONUS_OPTIONS)[number];

/** The bounds of the Total Rounds (glossary), mirroring the backend's range. */
export const TOTAL_ROUNDS_MIN = 1;
export const TOTAL_ROUNDS_MAX = 20;
export const TOTAL_ROUNDS_DEFAULT = 4;

/** Organizer-configurable tournament rules. Mirrors the backend settings value type. */
export interface TournamentSettings {
    grandTichuBonus: GrandTichuBonus;
    /** How many rounds the tournament runs; the one setting still adjustable while ACTIVE. */
    totalRounds: number;
}

/**
 * A tournament as the UI knows it. Decoupled from the wire format: timestamps are real
 * `Date` objects here, the DTO carries ISO strings, and mappers bridge the two.
 */
export interface Tournament {
    id: string;
    name: string;
    description: string;
    maxTeams: number;
    settings: TournamentSettings;
    status: TournamentStatus;
    createdAt: Date;
    updatedAt: Date;
}

/**
 * The editable fields of a tournament, as forms produce them. A domain-level input so
 * the layers above the repository never see the wire DTO.
 */
export interface TournamentWriteInput {
    name: string;
    description: string;
    maxTeams: number;
    settings: TournamentSettings;
}
