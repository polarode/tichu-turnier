import { describe, expect, it } from 'vitest';

import { tableLabel } from '@/domain/round.domain.ts';

describe('tableLabel', () => {
    it('letters tables from A', () => {
        expect(tableLabel(1)).toBe('A');
        expect(tableLabel(2)).toBe('B');
        expect(tableLabel(26)).toBe('Z');
    });

    it('continues with two letters past Z', () => {
        expect(tableLabel(27)).toBe('AA');
        expect(tableLabel(28)).toBe('AB');
        expect(tableLabel(52)).toBe('AZ');
        expect(tableLabel(53)).toBe('BA');
    });
});
