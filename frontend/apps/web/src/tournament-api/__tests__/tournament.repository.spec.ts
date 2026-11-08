import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import type { TournamentWriteInput } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { apiClient } from '@/shared/api/api-client.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

const dto = {
    id: 't-1',
    name: 'Spring Classic',
    description: 'annual invitational',
    maxTeams: 16,
    settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 4 },
    status: TournamentStatus.SETUP,
    createdAt: '2026-01-01T00:00:00.000Z',
    publicVisibility: false,
    organizerRole: OrganizerRole.LEAD,
    leadOrganizerEmail: 'lead@example.com',
    updatedAt: '2026-01-01T00:00:00.000Z',
};

const writeRequest: TournamentWriteInput = {
    name: 'Spring Classic',
    description: 'annual invitational',
    maxTeams: 16,
    settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 4 },
};

/** A normal table's four-game schedule: both teams play every game. */
function schedule(first: string, second: string) {
    return [1, 2, 3, 4].map((gameNumber) => ({ gameNumber, teamIds: [first, second] }));
}
const startedResponse = {
    tournament: { ...dto, status: TournamentStatus.ACTIVE },
    round: {
        roundNumber: 1,
        tables: [
            {
                tableNumber: 1,
                teamIds: ['team-a', 'team-b'],
                trio: false,
                status: 'PLAYING',
                confirmedTeamIds: [],
                gameCount: 4,
                schedule: schedule('team-a', 'team-b'),
                games: [],
            },
            {
                tableNumber: 2,
                teamIds: ['team-c', 'team-d'],
                trio: false,
                status: 'PLAYING',
                confirmedTeamIds: [],
                gameCount: 4,
                schedule: schedule('team-c', 'team-d'),
                games: [],
            },
        ],
    },
};

const roundsResponse = [
    {
        roundNumber: 1,
        tables: [
            {
                tableNumber: 1,
                teamIds: ['team-a', 'team-b'],
                trio: false,
                status: 'PLAYING',
                confirmedTeamIds: [],
                gameCount: 4,
                schedule: schedule('team-a', 'team-b'),
                games: [],
            },
        ],
    },
];

describe('TournamentRepository', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('lists tournaments and maps them to domain', async () => {
        mock.onGet('/api/tournaments').reply(200, [dto]);

        const tournaments = await TournamentRepository.list();

        expect(tournaments).toHaveLength(1);
        expect(tournaments[0]!.name).toBe('Spring Classic');
        expect(tournaments[0]!.createdAt).toBeInstanceOf(Date);
    });

    it('gets a single tournament', async () => {
        mock.onGet('/api/tournaments/t-1').reply(200, dto);

        const tournament = await TournamentRepository.get('t-1');

        expect(tournament.id).toBe('t-1');
        expect(tournament.status).toBe(TournamentStatus.SETUP);
    });

    it('creates a tournament with the write payload', async () => {
        mock.onPost('/api/tournaments').reply(201, dto);

        const tournament = await TournamentRepository.create(writeRequest);

        expect(tournament.id).toBe('t-1');
        expect(JSON.parse(mock.history.post[0]!.data)).toEqual(writeRequest);
    });

    it('updates a tournament with the write payload', async () => {
        mock.onPut('/api/tournaments/t-1').reply(200, dto);

        const tournament = await TournamentRepository.update('t-1', writeRequest);

        expect(tournament.id).toBe('t-1');
        expect(JSON.parse(mock.history.put[0]!.data)).toEqual(writeRequest);
    });

    it('deletes a tournament', async () => {
        mock.onDelete('/api/tournaments/t-1').reply(204);

        await expect(TournamentRepository.remove('t-1')).resolves.toBeUndefined();
    });

    it('surfaces the backend error code as a typed ApiError', async () => {
        mock.onPut('/api/tournaments/t-1').reply(409, {
            status: 409,
            code: 'TOURNAMENT_NOT_EDITABLE',
            detail: 'tournament has already started',
        });

        const error = await TournamentRepository.update('t-1', writeRequest).catch(
            (e: unknown) => e,
        );

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).code).toBe('TOURNAMENT_NOT_EDITABLE');
    });

    it('rejects a malformed response at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1').reply(200, { id: 't-1', name: 'Missing fields' });

        await expect(TournamentRepository.get('t-1')).rejects.toThrow();
    });
});

describe('TournamentRepository start/rounds', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('starts a tournament and maps the started tournament plus round 1 to domain', async () => {
        mock.onPost('/api/tournaments/t-1/start').reply(200, startedResponse);

        const { tournament, round } = await TournamentRepository.start('t-1');

        expect(tournament.status).toBe(TournamentStatus.ACTIVE);
        expect(round.number).toBe(1);
        expect(round.tables).toEqual([
            {
                tableNumber: 1,
                teamIds: ['team-a', 'team-b'],
                trio: false,
                confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
                gameCount: 4,
                schedule: schedule('team-a', 'team-b'),
                games: [],
            },
            {
                tableNumber: 2,
                teamIds: ['team-c', 'team-d'],
                trio: false,
                confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
                gameCount: 4,
                schedule: schedule('team-c', 'team-d'),
                games: [],
            },
        ]);
    });

    it('surfaces start-rejection codes as a typed ApiError', async () => {
        for (const code of ['TOURNAMENT_NOT_STARTABLE', 'INSUFFICIENT_TEAMS'] as const) {
            mock.onPost('/api/tournaments/t-1/start').replyOnce(409, {
                status: 409,
                code,
                detail: 'rejected',
            });

            const error = await TournamentRepository.start('t-1').catch((e: unknown) => e);

            expect(error).toBeInstanceOf(ApiError);
            expect((error as ApiError).code).toBe(code);
        }
    });

    it('lists rounds of a tournament mapped to domain', async () => {
        mock.onGet('/api/tournaments/t-1/rounds').reply(200, roundsResponse);

        const rounds = await TournamentRepository.rounds('t-1');

        expect(rounds).toEqual([
            {
                number: 1,
                tables: [
                    {
                        tableNumber: 1,
                        teamIds: ['team-a', 'team-b'],
                        trio: false,
                        confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
                        gameCount: 4,
                        schedule: schedule('team-a', 'team-b'),
                        games: [],
                    },
                ],
            },
        ]);
    });

    it('rejects a table seating more than three teams at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1/rounds').reply(200, [
            {
                roundNumber: 1,
                tables: [
                    {
                        tableNumber: 1,
                        teamIds: ['team-a', 'team-b', 'team-c', 'team-d'],
                        trio: true,
                        status: 'PLAYING',
                        confirmedTeamIds: [],
                        gameCount: 6,
                        schedule: [],
                        games: [],
                    },
                ],
            },
        ]);

        await expect(TournamentRepository.rounds('t-1')).rejects.toThrow();
    });

    it('rejects a malformed rounds response at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1/rounds').reply(200, [{ roundNumber: 'one' }]);

        await expect(TournamentRepository.rounds('t-1')).rejects.toThrow();
    });
});

describe('TournamentRepository standings/total rounds', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('reads the standings in server order, mapped to domain', async () => {
        mock.onGet('/api/tournaments/t-1/standings').reply(200, [
            {
                rank: 1,
                teamId: 'team-a',
                teamName: 'Alpha',
                gamesPlayed: 4,
                victoryPoints: 6,
                tichuPoints: 240,
            },
            {
                rank: 2,
                teamId: 'team-b',
                teamName: 'Beta',
                gamesPlayed: 4,
                victoryPoints: 2,
                tichuPoints: 160,
            },
        ]);

        const standings = await TournamentRepository.standings('t-1');

        expect(standings).toEqual([
            {
                rank: 1,
                teamId: 'team-a',
                teamName: 'Alpha',
                gamesPlayed: 4,
                victoryPoints: 6,
                tichuPoints: 240,
            },
            {
                rank: 2,
                teamId: 'team-b',
                teamName: 'Beta',
                gamesPlayed: 4,
                victoryPoints: 2,
                tichuPoints: 160,
            },
        ]);
    });

    it('rejects a standings line without a rank at the zod boundary', async () => {
        mock.onGet('/api/tournaments/t-1/standings').reply(200, [
            {
                teamId: 'team-a',
                teamName: 'Alpha',
                gamesPlayed: 4,
                victoryPoints: 6,
                tichuPoints: 240,
            },
        ]);

        await expect(TournamentRepository.standings('t-1')).rejects.toThrow();
    });

    it('sets the total rounds and returns the updated tournament', async () => {
        mock.onPut('/api/tournaments/t-1/total-rounds').reply(200, {
            ...dto,
            settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 6 },
        });

        const tournament = await TournamentRepository.setTotalRounds('t-1', 6);

        expect(tournament.settings.totalRounds).toBe(6);
        expect(JSON.parse(mock.history.put[0]!.data)).toEqual({ totalRounds: 6 });
    });
});

describe('TournamentRepository next round', () => {
    let mock: MockAdapter;

    beforeEach(() => {
        mock = new MockAdapter(apiClient);
    });

    afterEach(() => {
        mock.restore();
    });

    it('starts the next round and maps the new round to domain', async () => {
        mock.onPost('/api/tournaments/t-1/rounds').reply(200, {
            ...roundsResponse[0],
            roundNumber: 2,
        });

        const round = await TournamentRepository.startNextRound('t-1');

        expect(round.number).toBe(2);
        expect(round.tables[0]!.teamIds).toEqual(['team-a', 'team-b']);
    });

    it('surfaces advance-rejection codes as a typed ApiError', async () => {
        for (const code of [
            'ROUND_INCOMPLETE',
            'TOTAL_ROUNDS_REACHED',
            'TOURNAMENT_NOT_ACTIVE',
        ] as const) {
            mock.onPost('/api/tournaments/t-1/rounds').replyOnce(409, {
                status: 409,
                code,
                detail: 'rejected',
            });

            const error = await TournamentRepository.startNextRound('t-1').catch((e: unknown) => e);

            expect(error).toBeInstanceOf(ApiError);
            expect((error as ApiError).code).toBe(code);
        }
    });
});
