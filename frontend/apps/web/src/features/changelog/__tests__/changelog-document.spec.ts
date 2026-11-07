import { describe, expect, it } from 'vitest';

import { parseChangelog } from '@/features/changelog/changelog-schema.ts';
import document from '@/features/changelog/changelog.json';

describe('changelog document', () => {
    it('parses the shipped changelog.json without throwing', () => {
        expect(() => parseChangelog(document)).not.toThrow();
    });
});
