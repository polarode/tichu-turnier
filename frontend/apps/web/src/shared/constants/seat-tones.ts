/**
 * Team identity colours per seat at a table (A, B, C): each seated team keeps its
 * colour in every game it plays, so at a trio table game 3 (B vs C) reads Star vs
 * Pagoda. Literal class names so Tailwind picks them up.
 */
const SEAT_TONES = [
    { dot: 'bg-team-1', text: 'text-team-1', chip: 'bg-team-1-soft text-team-1' },
    { dot: 'bg-team-2', text: 'text-team-2', chip: 'bg-team-2-soft text-team-2' },
    { dot: 'bg-team-3', text: 'text-team-3', chip: 'bg-team-3-soft text-team-3' },
] as const;

export type SeatTone = (typeof SEAT_TONES)[number];

/** The tone of the 0-based [seat]; a table seats at most three teams. */
export function seatTone(seat: number): SeatTone {
    return SEAT_TONES[seat] ?? SEAT_TONES[0];
}
