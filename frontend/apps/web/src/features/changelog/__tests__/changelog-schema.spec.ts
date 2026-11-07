import { describe, expect, it } from 'vitest';
import { ZodError } from 'zod';

import { parseChangelog } from '@/features/changelog/changelog-schema.ts';

describe('parseChangelog', () => {
    it('returns entries newest first with UTC calendar dates and changes preserved', () => {
        const entries = parseChangelog([
            { date: '2026-10-01', changes: ['First release'] },
            { date: '2026-11-07', changes: ['Changelog page', 'Fix a bug'] },
            { date: '2026-10-15', changes: ['Improve the tournament list'] },
        ]);

        expect(entries).toEqual([
            {
                date: new Date('2026-11-07T00:00:00.000Z'),
                changes: ['Changelog page', 'Fix a bug'],
            },
            {
                date: new Date('2026-10-15T00:00:00.000Z'),
                changes: ['Improve the tournament list'],
            },
            {
                date: new Date('2026-10-01T00:00:00.000Z'),
                changes: ['First release'],
            },
        ]);
    });

    it('rejects an entry with an empty changes array', () => {
        expect(() => parseChangelog([{ date: '2026-11-07', changes: [] }])).toThrow(ZodError);
    });

    it('rejects a malformed date', () => {
        expect(() => parseChangelog([{ date: '07.11.2026', changes: ['New'] }])).toThrow(ZodError);
    });
});
