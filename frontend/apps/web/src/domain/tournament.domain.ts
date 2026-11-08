import type { StandingsEntry } from '@/domain/standings.domain.ts';
import type { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';
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
    /** Whether the signed-in organizer leads this tournament or only co-organizes it. */
    organizerRole: OrganizerRole;
    /** The email of the lead organizer, shown to co-organizers. */
    leadOrganizerEmail: string;
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

/** A player as the public tournament page shows them. */
export interface PublicPlayer {
    id: string;
    name: string;
}

/** A team's Table Totals at one table (GLOSSARY.md), as the public tournament page shows them. */
export interface PublicTableTotals {
    victoryPoints: number;
    tichuPoints: number;
}

/** A team as the public tournament page shows it: its players in order. */
export interface PublicTeam {
    id: string;
    name: string;
    players: PublicPlayer[];
    /** The team's Table Totals at this table; present only once the tournament is published. */
    totals?: PublicTableTotals;
}

/** A table of a public round: two teams, or three at the trio table. */
export interface PublicTable {
    tableNumber: number;
    trio: boolean;
    /** The teams in seat order. */
    teams: PublicTeam[];
}

/** A drawn round of a public tournament, with its tables. */
export interface PublicRound {
    number: number;
    tables: PublicTable[];
}

/**
 * A tournament as the public tournament page shows it: its pairings in every status, plus
 * the table totals and Final Ranking once it is published (no games, outcomes or table
 * status).
 */
export interface PublicTournamentDetail {
    id: string;
    name: string;
    status: TournamentStatus;
    rounds: PublicRound[];
    /** The Final Ranking; present only once the tournament is published. */
    finalRanking?: StandingsEntry[];
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
