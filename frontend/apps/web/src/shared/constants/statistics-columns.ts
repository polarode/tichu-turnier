/**
 * The team table columns after the always-shown Team column. The ids are stable: they are
 * the keys of the viewer's stored column choice, so renaming one hides it for everyone.
 */
export const STATISTICS_COLUMN_IDS = [
    'tichuCalls',
    'successfulSmallTichu',
    'successfulGrandTichu',
    'destroyedTichus',
    'bombCount',
    'bombsPerGame',
    'doubleWins',
    'beschissGames',
    'goodHorseRatio',
] as const;

export type StatisticsColumn = (typeof STATISTICS_COLUMN_IDS)[number];

/** The columns a phone-sized viewport shows when the viewer has not chosen. */
export const COMPACT_STATISTICS_COLUMNS: readonly StatisticsColumn[] = [
    'tichuCalls',
    'successfulSmallTichu',
    'successfulGrandTichu',
    'destroyedTichus',
];

/** Viewports at least this wide count as desktop for the default column choice. */
export const DESKTOP_MEDIA_QUERY = '(min-width: 768px)';
