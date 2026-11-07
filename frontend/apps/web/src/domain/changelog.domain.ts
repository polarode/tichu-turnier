/**
 * One dated entry of the changelog. The [date] is a calendar day, held as UTC midnight;
 * [changes] is the bulleted list of changes made that day, written in English.
 */
export interface ChangelogEntry {
    date: Date;
    changes: string[];
}
