import type { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/** Organizer-configurable tournament rules. Mirrors the backend settings value type. */
export interface TournamentSettings {
    allowGrandTichu: boolean;
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
