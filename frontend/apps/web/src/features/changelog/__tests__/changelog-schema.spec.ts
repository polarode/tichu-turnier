import { describe, expect, it } from 'vitest';
import { ZodError } from 'zod';

import { parseChangelog } from '@/features/changelog/changelog-schema.ts';

describe('parseChangelog', () => {
    it('returns entries newest first with UTC calendar dates and changes preserved', () => {
        const entries = parseChangelog([
            { date: '2026-10-01', changes: [{ type: 'feature', text: 'First release' }] },
            {
                date: '2026-11-07',
                changes: [
                    { type: 'feature', text: 'Changelog page' },
                    { type: 'fix', text: 'Fix a bug' },
                ],
            },
            {
                date: '2026-10-15',
                changes: [{ type: 'improvement', text: 'Improve the tournament list' }],
            },
        ]);

        expect(entries).toEqual([
            {
                date: new Date('2026-11-07T00:00:00.000Z'),
                changes: [
                    { type: 'feature', text: 'Changelog page' },
                    { type: 'fix', text: 'Fix a bug' },
                ],
            },
            {
                date: new Date('2026-10-15T00:00:00.000Z'),
                changes: [{ type: 'improvement', text: 'Improve the tournament list' }],
            },
            {
                date: new Date('2026-10-01T00:00:00.000Z'),
                changes: [{ type: 'feature', text: 'First release' }],
            },
        ]);
    });

    it('orders each entry feature, improvement, fix and keeps document order within a type', () => {
        const [entry] = parseChangelog([
            {
                date: '2026-11-07',
                changes: [
                    { type: 'fix', text: 'Fix first' },
                    { type: 'feature', text: 'Feature first' },
                    { type: 'improvement', text: 'Improvement first' },
                    { type: 'feature', text: 'Feature second' },
                ],
            },
        ]);

        expect(entry?.changes).toEqual([
            { type: 'feature', text: 'Feature first' },
            { type: 'feature', text: 'Feature second' },
            { type: 'improvement', text: 'Improvement first' },
            { type: 'fix', text: 'Fix first' },
        ]);
    });

    it('rejects a change with an unknown type', () => {
        expect(() =>
            parseChangelog([
                { date: '2026-11-07', changes: [{ type: 'chore', text: 'Bump dependencies' }] },
            ]),
        ).toThrow(ZodError);
    });

    it('rejects an entry with an empty changes array', () => {
        expect(() => parseChangelog([{ date: '2026-11-07', changes: [] }])).toThrow(ZodError);
    });

    it('rejects a malformed date', () => {
        expect(() =>
            parseChangelog([{ date: '07.11.2026', changes: [{ type: 'feature', text: 'New' }] }]),
        ).toThrow(ZodError);
    });
});
