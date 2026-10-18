import { describe, expect, it } from 'vitest';

import { de } from '@/shared/i18n/catalogs/de.ts';
import { en } from '@/shared/i18n/catalogs/en.ts';
import { ERROR_MESSAGE_KEYS } from '@/shared/i18n/error-messages.ts';
import { ERROR_CODES } from '@/vocabulary/error-code.enum.ts';

describe('error message mapping', () => {
    it('covers every backend error code plus the UNKNOWN fallback', () => {
        expect(Object.keys(ERROR_MESSAGE_KEYS).sort()).toEqual([...ERROR_CODES, 'UNKNOWN'].sort());
    });

    it('points at catalog keys that exist in both languages', () => {
        for (const key of Object.values(ERROR_MESSAGE_KEYS)) {
            expect(en[key], `en:${key}`).toBeTruthy();
            expect(de[key], `de:${key}`).toBeTruthy();
        }
    });
});
