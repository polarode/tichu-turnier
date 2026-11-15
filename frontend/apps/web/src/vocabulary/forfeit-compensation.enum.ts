/**
 * The Forfeit Compensation (GLOSSARY.md: Forfeit Game, ADR 0011) the organizer picks per
 * withdrawal: full (2 VP, 100 TP), half (1 VP, 50 TP) or none. Mirrors the backend enum;
 * shared by the domain model and the DTO schema.
 */
export const ForfeitCompensation = {
    FULL: 'FULL',
    HALF: 'HALF',
    NONE: 'NONE',
} as const;

export type ForfeitCompensation = (typeof ForfeitCompensation)[keyof typeof ForfeitCompensation];

export const FORFEIT_COMPENSATIONS = [
    ForfeitCompensation.FULL,
    ForfeitCompensation.HALF,
    ForfeitCompensation.NONE,
] as const;
