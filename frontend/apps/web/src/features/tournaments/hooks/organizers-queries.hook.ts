import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { organizersQueryKey } from '@/shared/constants/query-keys.ts';
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
