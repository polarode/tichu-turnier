/**
 * How a change reaches the organizers: a new capability, a change to something that
 * already existed, or a fix for a defect. Listed in display order.
 */
export const CHANGE_TYPES = ['feature', 'improvement', 'fix'] as const;

export type ChangeType = (typeof CHANGE_TYPES)[number];

/**
 * One change of a dated entry. The [text] is the bulleted line, written in English for
 * organizers; the [type] classifies it.
 */
export interface ChangelogChange {
    type: ChangeType;
    text: string;
}

/**
 * One dated entry of the changelog. The [date] is a calendar day, held as UTC midnight;
 * [changes] is the bulleted list of changes made that day, ordered features first, then
 * improvements, then fixes.
 */
export interface ChangelogEntry {
    date: Date;
    changes: ChangelogChange[];
}
