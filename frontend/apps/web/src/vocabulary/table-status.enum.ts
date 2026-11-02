/**
 * The Table Status (GLOSSARY.md, ADR 0006), derived by the backend from the
 * table's confirmations: no confirmation is PLAYING, at least one CONFIRMING
 * (games frozen), every seated team COMPLETED (final). Mirrors the backend enum;
 * shared by the domain model and the DTO schema.
 */
export const TableStatus = {
    PLAYING: 'PLAYING',
    CONFIRMING: 'CONFIRMING',
    COMPLETED: 'COMPLETED',
} as const;

export type TableStatus = (typeof TableStatus)[keyof typeof TableStatus];

export const TABLE_STATUSES = [TableStatus.PLAYING, TableStatus.CONFIRMING, TableStatus.COMPLETED] as const;
