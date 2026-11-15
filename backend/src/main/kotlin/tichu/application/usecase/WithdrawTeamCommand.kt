package tichu.application.usecase

import tichu.application.api.WithdrawTeam
import tichu.domain.AdminUserId
import tichu.domain.ForfeitCompensation
import tichu.domain.RoundRepository
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentEvents
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.domain.Withdrawal
import tichu.domain.WithdrawalReason
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TeamAlreadyWithdrawnException
import tichu.vocabulary.TournamentNotStartedException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The organizer's Withdrawal of a team (ADR 0011). A plain class wired as a bean in
 * [UseCaseConfiguration].
 *
 * One transaction. The tournament is row-locked first, then the organizer check runs
 * ([TournamentAccess]: the lead or a co-organizer, otherwise not-found). A withdrawal is
 * possible in any status after setup, even once published. It is stamped with the highest
 * drawn round, the instant from the injected [Clock] and the organizer. Standings are
 * computed on read, so the withdrawal reaches them without further writes.
 */
@Transactional
class WithdrawTeamCommand(
    private val tournamentRepository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
    private val roundRepository: RoundRepository,
    private val teamRepository: TeamRepository,
    private val clock: Clock,
    private val tournamentEvents: TournamentEvents,
) : WithdrawTeam {

    override fun withdraw(
        tournamentId: TournamentId,
        teamId: TeamId,
        reason: WithdrawalReason,
        compensation: ForfeitCompensation,
        admin: AdminUserId,
    ): Withdrawal {
        // Lock before reading, so a request that waited sees the state the first one committed.
        tournamentRepository.lock(tournamentId)
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, admin)
        if (tournament.status == TournamentStatus.SETUP) {
            throw TournamentNotStartedException(
                "Tournament ${tournamentId.value} has not started; teams can only withdraw once it has",
            )
        }
        val team = teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
        if (team.isWithdrawn) {
            throw TeamAlreadyWithdrawnException("Team ${teamId.value} has already withdrawn")
        }
        val roundNumber = roundRepository.findByTournament(tournamentId).maxOfOrNull { it.number }
            ?: throw TournamentNotStartedException("Tournament ${tournamentId.value} has no drawn round yet")

        val withdrawal = Withdrawal(reason, compensation, roundNumber, clock.instant(), admin)
        teamRepository.updateWithdrawal(teamId, tournamentId, withdrawal)

        tournamentEvents.publish(TournamentEvent(TournamentEventType.TEAM_WITHDRAWAL_CHANGED, tournamentId))

        return withdrawal
    }
}
