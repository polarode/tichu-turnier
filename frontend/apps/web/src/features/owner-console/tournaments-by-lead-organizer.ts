import type { Tournament } from '@/domain/tournament.domain.ts';

export interface TournamentsByLeadOrganizer {
    email: string;
    tournaments: Tournament[];
}

/**
 * Groups tournaments under their lead organizer's email. Groups are sorted alphabetically
 * by email; tournaments keep the server's order (newest first) inside each group.
 */
export function groupTournamentsByLeadOrganizer(
    tournaments: Tournament[],
): TournamentsByLeadOrganizer[] {
    const groups = new Map<string, Tournament[]>();
    for (const tournament of tournaments) {
        const group = groups.get(tournament.leadOrganizerEmail);
        if (group) group.push(tournament);
        else groups.set(tournament.leadOrganizerEmail, [tournament]);
    }
    return [...groups.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([email, groupTournaments]) => ({ email, tournaments: groupTournaments }));
}
