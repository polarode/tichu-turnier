package tichu.application.usecase

import tichu.application.api.GetStandings
import tichu.domain.AdminUserId
import tichu.domain.Standings
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.TournamentNotStartedException

/**
 * The admin's standings read: loads every round with its games and confirmations
 * and hands them to [Standings.compute], the one standings implementation the
 * next round's pairing uses as well. Owner-scoped; a tournament still in setup has
 * no standings yet (409).
 */
class GetStandingsQuery(
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
    private val playedRounds: PlayedRoundsReader,
) : GetStandings {
    override fun get(tournamentId: TournamentId, owner: AdminUserId): Standings {
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, owner)
        if (tournament.status == TournamentStatus.SETUP) {
            throw TournamentNotStartedException("Tournament ${tournamentId.value} has no standings before it starts")
        }
        val rounds = playedRounds.of(tournamentId)
        return Standings.compute(teamRepository.findAllByTournament(tournamentId), rounds, tournament.settings)
    }
}
