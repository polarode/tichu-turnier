import { z } from 'zod';

import type { ChangelogEntry } from '@/domain/changelog.domain.ts';

const CALENDAR_DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** UTC midnight of the `YYYY-MM-DD` calendar day [value]. */
function toUtcCalendarDay(value: string): Date {
    return new Date(`${value}T00:00:00Z`);
}

/** True when [value] is a real calendar day, so `2026-02-30` is rejected. */
function isRealCalendarDay(value: string): boolean {
    const parsed = toUtcCalendarDay(value);
    return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

const changelogEntrySchema = z.object({
    date: z
        .string()
        .regex(CALENDAR_DAY_PATTERN)
        .refine(isRealCalendarDay, { message: 'Not a real calendar day' })
        .transform(toUtcCalendarDay),
    changes: z.array(z.string().trim().min(1)).min(1),
});

/**
 * Format of the changelog document: a list of dated entries, each with at least one
 * non-empty change. Dates are `YYYY-MM-DD` calendar days.
 */
const changelogDocumentSchema = z.array(changelogEntrySchema);

/**
 * Validates the raw changelog document (throws `ZodError` when invalid) and maps it to
 * domain entries sorted newest first. The sort is stable, so entries sharing a date keep
 * their document order.
 */
export function parseChangelog(raw: unknown): ChangelogEntry[] {
    return changelogDocumentSchema.parse(raw).sort((a, b) => b.date.getTime() - a.date.getTime());
}
