import { describe, expect, it } from 'vitest';

import { type ForfeitGame, isSettled, type RecordedGame } from '@/domain/game.domain.ts';

const recorded = { gameNumber: 1 } as RecordedGame;
const forfeit = { gameNumber: 3 } as ForfeitGame;

describe('isSettled', () => {
    it('settles a game that is recorded', () => {
        expect(isSettled(1, [recorded], [forfeit])).toBe(true);
    });

    it('settles a game that is forfeited', () => {
        expect(isSettled(3, [recorded], [forfeit])).toBe(true);
    });

    it('leaves a game that is neither recorded nor forfeited missing', () => {
        expect(isSettled(2, [recorded], [forfeit])).toBe(false);
    });
});
