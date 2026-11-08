import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
    organizersQueryKey,
    QueryKeys,
    tournamentScopedQueryKeys,
} from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/**
 * TanStack Query hooks for a tournament's organizers (lead and co-organizers). Components
 * call these; they never touch the repository directly. There is no live event for the
 * organizer list, so the add mutation writes the list it returns straight into the cache.
 */
export function useOrganizers(tournamentId: string) {
    return useQuery({
        queryKey: organizersQueryKey(tournamentId),
        queryFn: () => TournamentRepository.listOrganizers(tournamentId),
    });
}

export function useAddCoOrganizer(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (email: string) => TournamentRepository.addCoOrganizer(tournamentId, email),
        onSuccess: (organizers) =>
            queryClient.setQueryData(organizersQueryKey(tournamentId), organizers),
    });
}

/** The lead organizer removes a co-organizer; the list refetches and the dashboard list follows. */
export function useRemoveCoOrganizer(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (adminUserId: string) =>
            TournamentRepository.removeCoOrganizer(tournamentId, adminUserId),
        onSuccess: () =>
            Promise.all([
                queryClient.invalidateQueries({ queryKey: organizersQueryKey(tournamentId) }),
                queryClient.invalidateQueries({ queryKey: [QueryKeys.TOURNAMENTS] }),
            ]),
    });
}

/**
 * A co-organizer leaves the tournament (the caller passes their own admin id). The
 * tournament's queries are dropped instead of refetched: a refetch would now hit not-found
 * and send the admin to the dashboard with the lost-access notice. The dashboard list is
 * refetched, so the tournament disappears from it.
 */
export function useLeaveTournament(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (adminUserId: string) =>
            TournamentRepository.removeCoOrganizer(tournamentId, adminUserId),
        onSuccess: async () => {
            for (const queryKey of tournamentScopedQueryKeys(tournamentId)) {
                queryClient.removeQueries({ queryKey });
            }
            await queryClient.invalidateQueries({ queryKey: [QueryKeys.TOURNAMENTS] });
        },
    });
}
