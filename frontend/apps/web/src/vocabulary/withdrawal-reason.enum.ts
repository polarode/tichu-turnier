/**
 * Why a team withdrew (GLOSSARY.md: Withdrawal, ADR 0011): excluded by the organizer
 * (DISQUALIFIED) or left on its own (VOLUNTARY). Mirrors the backend enum; shared by the
 * domain model and the DTO schema.
 */
export const WithdrawalReason = {
    DISQUALIFIED: 'DISQUALIFIED',
    VOLUNTARY: 'VOLUNTARY',
} as const;

export type WithdrawalReason = (typeof WithdrawalReason)[keyof typeof WithdrawalReason];

export const WITHDRAWAL_REASONS = [
    WithdrawalReason.DISQUALIFIED,
    WithdrawalReason.VOLUNTARY,
] as const;
