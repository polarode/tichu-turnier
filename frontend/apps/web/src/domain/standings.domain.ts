/**
 * One line of the Standings (glossary): a team's rank and its summed results over
 * the games of completed tables. Teams tied exactly in VP and TP share a rank; the
 * server computes and orders everything, the UI only renders it.
 */
export interface StandingsEntry {
    rank: number;
    teamId: string;
    teamName: string;
    gamesPlayed: number;
    victoryPoints: number;
    tichuPoints: number;
}
