package tichu.application.usecase

import tichu.application.api.ListPublicTournaments
import tichu.application.api.PublicTournament
import tichu.domain.TeamRepository
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus

/**
 * Builds the public home list: public tournaments in ACTIVE, COMPLETED or PUBLISHED,
 * with their team counts fetched in one bulk query (no per-tournament count).
 */
class ListPublicTournamentsQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) : ListPublicTournaments {

    override fun list(): List<PublicTournament> {
        val tournaments = tournamentRepository.findAllPublic()
        val teamCounts = teamRepository.countByTournaments(tournaments.map { it.id })

        return tournaments
            .map { tournament ->
                PublicTournament(
                    id = tournament.id,
                    name = tournament.name,
                    status = tournament.status,
                    teamCount = teamCounts[tournament.id] ?: 0,
                )
            }
            .sortedWith(
                compareBy<PublicTournament> { TournamentStatus.PUBLICLY_LISTABLE.indexOf(it.status) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
    }
}
