import { vi, type Mock } from 'vitest';

type TournamentRepositoryModule = typeof import('@/tournament-api/tournament.repository.ts');
type TournamentRepositoryMethod = keyof TournamentRepositoryModule['TournamentRepository'];

/** Per-test default behaviour for repository methods, e.g. `{ list: vi.fn().mockResolvedValue([]) }`. */
export type TournamentRepositoryOverrides = Partial<Record<TournamentRepositoryMethod, Mock>>;

/**
 * Builds the module body for `vi.mock('@/tournament-api/tournament.repository.ts', ...)`.
 *
 * Every key of the real `TournamentRepository` becomes a `vi.fn()`, read from the actual module,
 * so a newly added repository method is mocked in every spec without touching the specs.
 * Call it from an async factory with a dynamic import (vi.mock is hoisted above imports):
 *
 * ```ts
 * vi.mock('@/tournament-api/tournament.repository.ts', async () => {
 *     const { mockTournamentRepositoryModule } = await import('@/test-helpers/tournament-repository.mock.ts');
 *     return mockTournamentRepositoryModule({ list: vi.fn().mockResolvedValue([]) });
 * });
 * ```
 */
export async function mockTournamentRepositoryModule(
    overrides: TournamentRepositoryOverrides = {},
): Promise<{ TournamentRepository: Record<TournamentRepositoryMethod, Mock> }> {
    const actual = await vi.importActual<TournamentRepositoryModule>(
        '@/tournament-api/tournament.repository.ts',
    );
    const methods = Object.fromEntries(
        Object.keys(actual.TournamentRepository).map((method) => [method, vi.fn()]),
    ) as Record<TournamentRepositoryMethod, Mock>;
    return {
        TournamentRepository: { ...methods, ...overrides } as Record<
            TournamentRepositoryMethod,
            Mock
        >,
    };
}
