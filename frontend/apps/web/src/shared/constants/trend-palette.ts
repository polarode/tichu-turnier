/**
 * Line colours of the Team Trend chart, one per team in the order the server lists them.
 * Mid-tone hues so each line stays readable on both the light and the dark card. Literal
 * values because the chart library takes colours as plain strings, not Tailwind classes.
 */
export const TREND_PALETTE = [
    '#2563eb',
    '#dc2626',
    '#16a34a',
    '#d97706',
    '#9333ea',
    '#0891b2',
    '#db2777',
    '#65a30d',
] as const;

/** The line colour of the [index]-th team; the palette repeats past its last colour. */
export function trendColor(index: number): string {
    return TREND_PALETTE[index % TREND_PALETTE.length] ?? TREND_PALETTE[0];
}
