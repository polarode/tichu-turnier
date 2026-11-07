import { z } from 'zod';

import {
    CHANGE_TYPES,
    type ChangelogChange,
    type ChangelogEntry,
    type ChangeType,
} from '@/domain/changelog.domain.ts';

const CALENDAR_DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Position of [type] in [CHANGE_TYPES], which lists the display order; the sort key of a change. */
function changeTypeRank(type: ChangeType): number {
    return CHANGE_TYPES.indexOf(type);
}

/**
 * Sorts [changes] by type order. `Array.prototype.sort` is stable, so document order holds
 * within a type.
 */
function sortChangesByType(changes: ChangelogChange[]): ChangelogChange[] {
    return changes.sort((a, b) => changeTypeRank(a.type) - changeTypeRank(b.type));
}

/** UTC midnight of the `YYYY-MM-DD` calendar day [value]. */
function toUtcCalendarDay(value: string): Date {
    return new Date(`${value}T00:00:00Z`);
}

/** True when [value] is a real calendar day, so `2026-02-30` is rejected. */
function isRealCalendarDay(value: string): boolean {
    const parsed = toUtcCalendarDay(value);
    return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

const changelogChangeSchema = z.object({
    type: z.enum(CHANGE_TYPES),
    text: z.string().trim().min(1),
});

const changelogEntrySchema = z.object({
    date: z
        .string()
        .regex(CALENDAR_DAY_PATTERN)
        .refine(isRealCalendarDay, { message: 'Not a real calendar day' })
        .transform(toUtcCalendarDay),
    changes: z.array(changelogChangeSchema).min(1).transform(sortChangesByType),
});

/**
 * Format of the changelog document: a list of dated entries, each with at least one
 * change. Dates are `YYYY-MM-DD` calendar days; each change has a type of `feature`,
 * `improvement` or `fix` and a non-empty text.
 */
const changelogDocumentSchema = z.array(changelogEntrySchema);

/**
 * Validates the raw changelog document (throws `ZodError` when invalid) and maps it to
 * domain entries sorted newest first. Within an entry, changes are ordered features, then
 * improvements, then fixes. Both sorts are stable, so document order holds within a date
 * and within a type.
 */
export function parseChangelog(raw: unknown): ChangelogEntry[] {
    return changelogDocumentSchema.parse(raw).sort((a, b) => b.date.getTime() - a.date.getTime());
}
