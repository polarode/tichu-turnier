import type { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/**
 * Tichu Points a grand Tichu call wins or loses: 200 under the normal rules, or 100 to
 * score it like a small Tichu.
 */
export const GRAND_TICHU_BONUS_OPTIONS = [100, 200] as const;
export type GrandTichuBonus = (typeof GRAND_TICHU_BONUS_OPTIONS)[number];

/**
 * Tichu Points a double win earns the winning team: 200 under the normal rules, or 100.
 * The extra Victory Point is unaffected.
 */
export const DOUBLE_WIN_BONUS_OPTIONS = [100, 200] as const;
export type DoubleWinBonus = (typeof DOUBLE_WIN_BONUS_OPTIONS)[number];

/** The bounds of the Total Rounds (glossary), mirroring the backend's range. */
export const TOTAL_ROUNDS_MIN = 1;
export const TOTAL_ROUNDS_MAX = 20;
export const TOTAL_ROUNDS_DEFAULT = 4;

/** Organizer-configurable tournament rules. Mirrors the backend settings value type. */
export interface TournamentSettings {
    grandTichuBonus: GrandTichuBonus;
    doubleWinBonus: DoubleWinBonus;
    /** How many rounds the tournament runs; the one setting still adjustable while ACTIVE. */
    totalRounds: number;
}

/** The settings that set what a game's bonuses are worth, i.e. what the live totals need. */
export type ScoringBonuses = Pick<TournamentSettings, 'grandTichuBonus' | 'doubleWinBonus'>;

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
    /** Whether the tournament is listed on the public home page (set by the organizer). */
    publicVisibility: boolean;
    createdAt: Date;
    updatedAt: Date;
}

/**
 * A tournament as the public (anonymous) home page shows it: public facts only, no
 * organizer or team details.
 */
export interface PublicTournament {
    id: string;
    name: string;
    status: TournamentStatus;
    teamCount: number;
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
