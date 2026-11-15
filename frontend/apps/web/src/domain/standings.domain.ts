import type { WithdrawalReason } from '@/vocabulary/withdrawal-reason.enum.ts';

/**
 * One line of the Standings (glossary): a team's rank and its summed results over
 * the games of completed tables. Teams tied exactly in VP and TP share a rank; the
 * server computes and orders everything, the UI only renders it.
 */
export interface StandingsEntry {
    /** The shared rank; null for a disqualified team, which is listed last without one. */
    rank: number | null;
    teamId: string;
    teamName: string;
    gamesPlayed: number;
    victoryPoints: number;
    tichuPoints: number;
    /** Why the team withdrew (visible to everyone, ADR 0011); null while it plays on. */
    withdrawalReason: WithdrawalReason | null;
}
