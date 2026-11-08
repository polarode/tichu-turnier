package tichu.application.api

import tichu.domain.TournamentId

interface GetPublicStatistics {
    /**
     * The tournament statistics as a spectator sees them (S12, #121). Anonymous, so it takes no
     * principal. Throws `NotFoundException` unless the tournament is public and visible to
     * spectators (setup, non-public and unknown ids look alike), and
     * [tichu.vocabulary.ResultsNotPublishedException] until the organizer has published the results.
     */
    fun get(id: TournamentId): TournamentStatistics
}
