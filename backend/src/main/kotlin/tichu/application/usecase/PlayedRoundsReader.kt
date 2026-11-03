package tichu.application.usecase

import tichu.domain.GameRepository
import tichu.domain.PlayedRound
import tichu.domain.RoundRepository
import tichu.domain.TableConfirmationRepository
import tichu.domain.TournamentId

/**
 * Reads every round of a tournament with its entered games and confirmations: the
 * input of the Standings, shared by the standings read and the next round's
 * pairing. A plain class wired as a bean in [UseCaseConfiguration].
 */
class PlayedRoundsReader(
    private val roundRepository: RoundRepository,
    private val gameRepository: GameRepository,
    private val confirmationRepository: TableConfirmationRepository,
) {
    /** The tournament's rounds in round order, each with its games and confirmed teams per table. */
    fun of(tournamentId: TournamentId): List<PlayedRound> = roundRepository.findByTournament(tournamentId).map { round ->
        PlayedRound(
            round = round,
            games = gameRepository.findByRound(tournamentId, round.number),
            confirmations = confirmationRepository.findByRound(tournamentId, round.number),
        )
    }
}
