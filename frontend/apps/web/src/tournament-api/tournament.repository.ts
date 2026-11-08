import {
    tournamentDtoToDomain,
    tournamentWriteInputToDto,
} from '@/tournament-api/mappers/tournament.mapper.ts';
import { roundDtoToDomain } from '@/tournament-api/mappers/round.mapper.ts';
import {
    tournamentDtoSchema,
    tournamentListDtoSchema,
} from '@/tournament-api/dtos/tournament.dto.ts';
import {
    roundDtoSchema,
    roundListDtoSchema,
    startedTournamentDtoSchema,
} from '@/tournament-api/dtos/round.dto.ts';
import { standingsDtoSchema } from '@/tournament-api/dtos/standings.dto.ts';
import type {
    PublicVisibilityRequestDto,
    TotalRoundsRequestDto,
} from '@/tournament-api/dtos/tournament.dto.ts';
import {
    publicTournamentDetailDtoToDomain,
    publicTournamentDtoToDomain,
} from '@/tournament-api/mappers/public-tournament.mapper.ts';
import {
    publicTournamentDetailDtoSchema,
    publicTournamentListDtoSchema,
} from '@/tournament-api/dtos/public-tournament.dto.ts';
import { standingsEntryDtoToDomain } from '@/tournament-api/mappers/standings.mapper.ts';
import { organizersDtoToDomain } from '@/tournament-api/mappers/organizers.mapper.ts';
import { organizersDtoSchema } from '@/tournament-api/dtos/organizers.dto.ts';
import type { AddCoOrganizerRequestDto } from '@/tournament-api/dtos/organizers.dto.ts';
import type { Organizers } from '@/domain/organizers.domain.ts';
import type { StandingsEntry } from '@/domain/standings.domain.ts';
import type {
    PublicTournament,
    PublicTournamentDetail,
    Tournament,
    TournamentWriteInput,
} from '@/domain/tournament.domain.ts';
import type { Round } from '@/domain/round.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the tournament endpoints and wire DTOs. Each function parses
 * the response with zod and maps it to a domain `Tournament`; ownership is enforced
 * server-side from the session cookie.
 */
async function list(): Promise<Tournament[]> {
    const { data } = await apiClient.get<unknown>('/api/tournaments');
    return tournamentListDtoSchema.parse(data).map(tournamentDtoToDomain);
}

async function get(id: string): Promise<Tournament> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}`);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function create(input: TournamentWriteInput): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>(
        '/api/tournaments',
        tournamentWriteInputToDto(input),
    );
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function update(id: string, input: TournamentWriteInput): Promise<Tournament> {
    const { data } = await apiClient.put<unknown>(
        `/api/tournaments/${id}`,
        tournamentWriteInputToDto(input),
    );
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function start(id: string): Promise<{ tournament: Tournament; round: Round }> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/start`);
    const { tournament, round } = startedTournamentDtoSchema.parse(data);
    return {
        tournament: tournamentDtoToDomain(tournament),
        round: roundDtoToDomain(round),
    };
}

async function rounds(id: string): Promise<Round[]> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}/rounds`);
    return roundListDtoSchema.parse(data).map(roundDtoToDomain);
}

/** Starts the next round, drawn by the server from the current standings. */
async function startNextRound(id: string): Promise<Round> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/rounds`);
    return roundDtoToDomain(roundDtoSchema.parse(data));
}

/** The admin-only standings, in the server's order (ranks and ties computed there). */
async function standings(id: string): Promise<StandingsEntry[]> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}/standings`);
    return standingsDtoSchema.parse(data).map(standingsEntryDtoToDomain);
}

/** Changes only the Total Rounds; the one setting still editable while ACTIVE. */
async function setTotalRounds(id: string, totalRounds: number): Promise<Tournament> {
    const body: TotalRoundsRequestDto = { totalRounds };
    const { data } = await apiClient.put<unknown>(`/api/tournaments/${id}/total-rounds`, body);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

/** Shows or hides the tournament on the public home page; returns the updated tournament. */
async function setPublicVisibility(id: string, publicVisibility: boolean): Promise<Tournament> {
    const body: PublicVisibilityRequestDto = { publicVisibility };
    const { data } = await apiClient.put<unknown>(`/api/tournaments/${id}/public-visibility`, body);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

/** The anonymous home-page list of publicly visible tournaments (public facts only). */
async function listPublicTournaments(): Promise<PublicTournament[]> {
    const { data } = await apiClient.get<unknown>('/api/public/tournaments');
    return publicTournamentListDtoSchema.parse(data).map(publicTournamentDtoToDomain);
}

/** The anonymous pairings of one public tournament (public facts only, every status). */
async function getPublicTournament(id: string): Promise<PublicTournamentDetail> {
    const { data } = await apiClient.get<unknown>(`/api/public/tournaments/${id}`);
    return publicTournamentDetailDtoToDomain(publicTournamentDetailDtoSchema.parse(data));
}

/** Finishes the tournament once every round is played; fixes the Final Ranking (organizer-only until published). */
async function finish(id: string): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/finish`);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

/** Publishes the Final Ranking so the teams can see it. */
async function publish(id: string): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/publish`);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function remove(id: string): Promise<void> {
    await apiClient.delete(`/api/tournaments/${id}`);
}

/** The lead organizer and the co-organizers of one tournament, in the order they were added. */
async function listOrganizers(id: string): Promise<Organizers> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}/organizers`);
    return organizersDtoToDomain(organizersDtoSchema.parse(data));
}

/** Adds an active admin as co-organizer (lead organizer only); returns the updated list. */
async function addCoOrganizer(id: string, email: string): Promise<Organizers> {
    const body: AddCoOrganizerRequestDto = { email };
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/organizers`, body);
    return organizersDtoToDomain(organizersDtoSchema.parse(data));
}

/** Removes a co-organizer (lead organizer), or leaves the tournament (a co-organizer removing themself). */
async function removeCoOrganizer(id: string, adminUserId: string): Promise<void> {
    await apiClient.delete(`/api/tournaments/${id}/organizers/${adminUserId}`);
}

export const TournamentRepository = {
    list,
    get,
    create,
    update,
    remove,
    start,
    rounds,
    standings,
    setTotalRounds,
    setPublicVisibility,
    listPublicTournaments,
    getPublicTournament,
    startNextRound,
    finish,
    publish,
    listOrganizers,
    addCoOrganizer,
    removeCoOrganizer,
};
