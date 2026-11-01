import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import {
    DARK_SCHEME_QUERY,
    DEFAULT_THEME_PREFERENCE,
    THEME_PREFERENCES,
    THEME_STORAGE_KEY,
} from '@/shared/theme/theme.ts';

/**
 * index.html sets the theme in an inline script before React loads, so it
 * cannot import theme.ts. This guard fails when the two drift apart, which
 * would bring back a flash of the wrong theme on load.
 */
describe('index.html pre-paint theme script', () => {
    const html = readFileSync(resolve(__dirname, '../../../../index.html'), 'utf8');

    it('uses the same storage key, values, default and media query as theme.ts', () => {
        expect(html).toContain(`'${THEME_STORAGE_KEY}'`);
        for (const preference of THEME_PREFERENCES) {
            expect(html).toContain(`'${preference}'`);
        }
        expect(html).toContain(`var preference = '${DEFAULT_THEME_PREFERENCE}'`);
        expect(html).toContain(`'${DARK_SCHEME_QUERY}'`);
    });
});
