import { describe, expect, it } from 'vitest';

import { fillPlaceholders } from '@/shared/i18n/fill-placeholders.ts';

describe('fillPlaceholders', () => {
    it('replaces a placeholder with its value', () => {
        expect(fillPlaceholders('Du hast {name} verlassen.', { name: 'Jahresturnier' })).toBe(
            'Du hast Jahresturnier verlassen.',
        );
    });

    it('replaces every occurrence of a repeated placeholder', () => {
        expect(fillPlaceholders('{name} und {name}', { name: 'Ada' })).toBe('Ada und Ada');
    });

    it('leaves a placeholder without a matching value as it is', () => {
        expect(fillPlaceholders('{name} hat {count} Punkte', { name: 'Ada' })).toBe(
            'Ada hat {count} Punkte',
        );
    });

    it('returns the template unchanged when there are no values', () => {
        expect(fillPlaceholders('Ohne {name}')).toBe('Ohne {name}');
    });
});
