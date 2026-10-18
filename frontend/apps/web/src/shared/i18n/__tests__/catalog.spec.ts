import { describe, expect, it } from 'vitest';

import { de } from '@/shared/i18n/catalogs/de.ts';
import { en } from '@/shared/i18n/catalogs/en.ts';

/**
 * Catalog-coverage check. A user-facing string that exists in one catalog but
 * not the other — or that is left empty — fails CI.
 */
describe('i18n catalogs', () => {
    it('cover exactly the same set of keys', () => {
        expect(Object.keys(de).sort()).toEqual(Object.keys(en).sort());
    });

    it('translate every key to a non-empty string in both languages', () => {
        for (const [key, value] of Object.entries(en)) {
            expect(value.trim(), `en:${key}`).not.toBe('');
        }
        for (const [key, value] of Object.entries(de)) {
            expect(value.trim(), `de:${key}`).not.toBe('');
        }
    });
});
