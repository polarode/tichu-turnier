/**
 * Line colours of the Team Trend chart, one per team in the order the server lists them.
 * Mid-tone hues so each line stays readable on both the light and the dark card. Literal
 * values because the chart library takes colours as plain strings, not Tailwind classes.
 *
 * Ordered so the first lines stay furthest apart, including under red-green colour
 * blindness (simulated deuteranopia and protanopia): a small tournament's teams get the
 * clearest pairs, and red and green only come fifth and sixth.
 */
export const TREND_PALETTE = [
    '#d97706', // amber
    '#9333ea', // purple
    '#db2777', // pink
    '#0891b2', // cyan
    '#dc2626', // red
    '#16a34a', // green
    '#65a30d', // lime
    '#2563eb', // blue
] as const;

/** The line colour of the [index]-th team; the palette repeats past its last colour. */
export function trendColor(index: number): string {
    return TREND_PALETTE[index % TREND_PALETTE.length] ?? TREND_PALETTE[0];
}
